package io.undertow.websockets.jsr;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.core.ContextClassLoaderSetupAction;
import io.undertow.servlet.spec.ServletContextImpl;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class Bootstrap implements ServletExtension {

    public static final String FILTER_NAME = "Undertow Web Socket Filter";

    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
        WebSocketDeploymentInfo info = (WebSocketDeploymentInfo) deploymentInfo.getServletContextAttributes().get(WebSocketDeploymentInfo.ATTRIBUTE_NAME);

        if (info == null) {
            return;
        }
        final List<ThreadSetupAction> setup = new ArrayList<ThreadSetupAction>();
        setup.add(new ContextClassLoaderSetupAction(deploymentInfo.getClassLoader()));
        setup.addAll(deploymentInfo.getThreadSetupActions());
        final CompositeThreadSetupAction threadSetupAction = new CompositeThreadSetupAction(setup);
        ServerWebSocketContainer container = new ServerWebSocketContainer(deploymentInfo.getClassIntrospecter(), info.getWorker(), info.getBuffers(), threadSetupAction, false);
        try {
            for (Class<?> annotation : info.getAnnotatedEndpoints()) {
                container.addEndpoint(annotation);
            }
            for(ServerEndpointConfig programatic : info.getProgramaticEndpoints()) {
                container.addEndpoint(programatic);
            }
        } catch (DeploymentException e) {
            throw new RuntimeException(e);
        }
        servletContext.setAttribute(ServerContainer.class.getName(), container);
        info.containerReady(container);
        SecurityActions.addContainer(deploymentInfo.getClassLoader(), container);

        deploymentInfo.addListener(Servlets.listener(WebSocketListener.class));
    }

    private static final class WebSocketListener implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServerWebSocketContainer container = (ServerWebSocketContainer) sce.getServletContext().getAttribute(ServerContainer.class.getName());
            FilterRegistration.Dynamic filter = sce.getServletContext().addFilter(FILTER_NAME, JsrWebSocketFilter.class);
            filter.setAsyncSupported(true);
            if(!container.getConfiguredServerEndpoints().isEmpty()){
                filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
            } else {
                container.setContextToAddFilter((ServletContextImpl) sce.getServletContext());
            }
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            SecurityActions.removeContainer(sce.getServletContext().getClassLoader());

        }
    }

}

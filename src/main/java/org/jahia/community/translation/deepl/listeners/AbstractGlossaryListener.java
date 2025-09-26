package org.jahia.community.translation.deepl.listeners;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRItemWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.List;
import java.util.stream.IntStream;

public abstract class AbstractGlossaryListener extends DefaultEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AbstractGlossaryListener.class);

    protected static final int NODE_EVENTS = IntStream.of(Event.NODE_ADDED, Event.NODE_REMOVED, Event.NODE_MOVED).sum();
    protected static final int PROPERTY_EVENTS = IntStream.of(Event.PROPERTY_ADDED, Event.PROPERTY_REMOVED, Event.PROPERTY_CHANGED).sum();

    public AbstractGlossaryListener() {
        setWorkspace(Constants.EDIT_WORKSPACE);
    }

    @Override
    public void onEvent(EventIterator events) {
        while (events.hasNext()) {
            final Event event = events.nextEvent();
            try {
                JCRTemplate.getInstance()
                        .doExecuteWithSystemSessionAsUser(
                                JCRSessionFactory.getInstance().getCurrentUser(),
                                Constants.EDIT_WORKSPACE,
                                null,
                                session -> {
                                    processEvent(event, session);
                                    return null;
                                });
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
    }

    private void processEvent(Event event, JCRSessionWrapper session) {
        final String itemPath;
        try {
            itemPath = event.getPath();
        } catch (RepositoryException e) {
            logger.error("", e);
            return;
        }

        final int eventType = event.getType();
        switch (eventType) {
            case Event.NODE_REMOVED:
                final List<String> nodeTypes = (event instanceof JCRObservationManager.EventWrapper) ? ((JCRObservationManager.EventWrapper) event).getNodeTypes() : null;
                processNodeEvent(null, itemPath, nodeTypes, eventType);
                break;
            case Event.PROPERTY_REMOVED:
                final String nodePath = StringUtils.substringBeforeLast(itemPath, "/");
                final String propertyName = StringUtils.substringAfterLast(itemPath, "/");
                processPropertyEvent(getNode(nodePath, session), nodePath, propertyName, eventType);
                break;
            default:
                try {
                    final JCRItemWrapper item = session.getItem(itemPath);
                    if (item.isNode()) {
                        processNodeEvent((JCRNodeWrapper) item, itemPath, null, eventType);
                    } else {
                        final JCRNodeWrapper node = item.getParent();
                        processPropertyEvent(node, node.getPath(), item.getName(), eventType);
                    }
                } catch (RepositoryException e) {
                    logger.error("", e);
                }
        }
    }

    private JCRNodeWrapper getNode(String path, JCRSessionWrapper session) {
        try {
            return session.getNode(path);
        } catch (RepositoryException e) {
            return null;
        }
    }

    protected void processNodeEvent(JCRNodeWrapper node, String nodePath, List<String> nodeTypes, int eventType) {
        logger.error("No such event should be processed: {}", eventType);
    }

    protected void processPropertyEvent(JCRNodeWrapper node, String nodePath, String propertyName, int eventType) {
        logger.error("No such event should be processed: {}", eventType);
    }
}

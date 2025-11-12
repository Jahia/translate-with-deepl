package org.jahia.community.translation.deepl.listeners;

import org.apache.commons.lang.StringUtils;
import org.jahia.community.translation.deepl.DeeplConstants;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

public class GlossarySetttingsListener extends AbstractGlossaryListener {

    private static final Logger logger = LoggerFactory.getLogger(GlossarySetttingsListener.class);

    @Override
    public int getEventTypes() {
        return PROPERTY_EVENTS;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{DeeplConstants.NT_DEEPL_SETTINGS};
    }

    @Override
    protected void processPropertyEvent(JCRNodeWrapper node, String nodePath, String propertyName, int eventType) {
        if (StringUtils.equals(propertyName, DeeplConstants.PROP_FORCE_RECREATION)) {
            try {
                if (node != null && node.hasProperty(DeeplConstants.PROP_FORCE_RECREATION) && node.getProperty(DeeplConstants.PROP_FORCE_RECREATION).getBoolean()) {
                    final DeepLTranslatorService service = BundleUtils.getOsgiService(DeepLTranslatorService.class, null);
                    service.getGlossaryManager().recreateGlossary();
                    node.setProperty(DeeplConstants.PROP_FORCE_RECREATION, false);
                    node.saveSession();
                }
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
    }
}

/*
dplnt:settings
    Node events:
        added: nothing to do
        removed: not supported, we do not delete the glossary
        moved: not supported
    Property events:
        forceRecreation: any event to be handled
        defaultGlossary: not supported

 */

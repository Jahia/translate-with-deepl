package org.jahia.community.translation.deepl.listeners;

import org.apache.commons.lang.StringUtils;
import org.jahia.community.translation.deepl.DeeplConstants;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRItemWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import java.util.List;
import java.util.stream.IntStream;

public class GlossaryEntryListener extends AbstractGlossaryListener {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryEntryListener.class);

    @Override
    public int getEventTypes() {
        return PROPERTY_EVENTS;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{DeeplConstants.NT_GLOSSARY_ENTRY};
    }

    @Override
    protected void processPropertyEvent(JCRNodeWrapper node, String nodePath, String propertyName, int eventType) {
        if (StringUtils.equals(propertyName, DeeplConstants.PROP_GLOSSARY_REF) && node != null) {
            final DeepLTranslatorService service = BundleUtils.getOsgiService(DeepLTranslatorService.class, null);
            service.getGlossaryManager().refreshGlossary();
        }
    }
}

/*

dplmix:glossaryEntry
    Node events:
        added: nothing to do, we focus ont the property
        removed: not supported, we do not delete the glossary
        moved: not supported
    Property events:
        glossary: sync the new file. No cleanup related to the previous one done.
        sourceLang / targetLang (for simple entries): changes are not supported

 */

package org.jahia.community.translation.deepl.listeners;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.DeeplConstants;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.observation.Event;
import java.util.List;

public class GlossaryDataListener extends AbstractGlossaryListener {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryDataListener.class);

    @Override
    public String[] getNodeTypes() {
        return new String[]{DeeplConstants.NT_GLOSSARY_ENTRY_DATA};
    }

    @Override
    public int getEventTypes() {
        return Event.PROPERTY_CHANGED;
    }

    @Override
    protected void processPropertyEvent(JCRNodeWrapper node, String nodePath, String propertyName, int eventType) {
        if (StringUtils.equals(propertyName, Constants.JCR_DATA) && node != null) {
            final DeepLTranslatorService service = BundleUtils.getOsgiService(DeepLTranslatorService.class, null);
            service.getGlossaryManager().refreshGlossary();
        }
    }
}

/*

dplmix:glossaryEntryData
    Node events:
        added: nothing to do
        removed: not supported, we do not delete the glossary
        moved: not supported
    Property events:
        jcr:content: changed


 */

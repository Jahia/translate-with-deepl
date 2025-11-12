package org.jahia.community.translation.deepl.service;

import org.jahia.community.translation.deepl.service.impl.GlossaryManager;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.Locale;

public interface DeepLTranslatorService {

    DeepLTranslationResponse translate(JCRNodeWrapper node, boolean translateSubtree, String sourceLanguage, String targetLanguage, boolean allLanguages, Locale responseLocale) throws RepositoryException;

    public GlossaryManager getGlossaryManager();
}

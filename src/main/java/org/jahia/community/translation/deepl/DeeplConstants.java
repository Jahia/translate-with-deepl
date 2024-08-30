package org.jahia.community.translation.deepl;

import org.jahia.api.Constants;

public class DeeplConstants {

    public static final String SERVICE_CONFIG_FILE_NAME = "org.jahia.community.translatewithdeepl";
    public static final String SERVICE_CONFIG_FILE_FULLNAME = SERVICE_CONFIG_FILE_NAME + ".cfg";
    public static final String PROP_API_KEY = "translation.deepl.api.key";
    public static final String PROP_DO_NOT_CONSIDER_PUBLICATION_STATUS = "translation.deepl.doNotConsiderPublicationStatus";
    public static final String PROP_PREFIX_TARGET_LANGUAGES = "targetLanguages.";

    public static final String SUBTREE_ITERABLE_TYPES = Constants.JAHIANT_PAGE + "," + Constants.JAHIANT_CONTENT;
    public static final String PROP_ALL_LANGUAGES = "allLanguages";
    public static final String PROP_SRC_LANGUAGE = "srcLanguage";
    public static final String PROP_DEST_LANGUAGE = "destLanguage";
    public static final String PROP_SUB_TREE = "subTree";

    public static final String TRANSLATE_PERMISSION = "deeplTranslate";

    private DeeplConstants() {

    }

}

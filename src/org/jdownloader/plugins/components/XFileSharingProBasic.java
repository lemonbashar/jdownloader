package org.jdownloader.plugins.components;

import java.awt.Color;
//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtPasswordField;
import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.DebugMode;
import org.appwork.utils.Exceptions;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.URLEncode;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptcha;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter.VideoExtensions;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.gui.InputChangedCallbackInterface;
import org.jdownloader.plugins.accounts.AccountBuilderInterface;
import org.jdownloader.plugins.components.config.XFSConfigVideo;
import org.jdownloader.plugins.components.config.XFSConfigVideo.PreferredDownloadQuality;
import org.jdownloader.plugins.components.config.XFSConfigVideo.PreferredStreamQuality;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.gui.swing.components.linkbutton.JLink;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.Form.MethodType;
import jd.parser.html.HTMLParser;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountRequiredException;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DefaultEditAccountPanel;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.SiteType.SiteTemplate;
import jd.plugins.hoster.RTMPDownload;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = {}, urls = {})
public class XFileSharingProBasic extends antiDDoSForHost {
    public XFileSharingProBasic(PluginWrapper wrapper) {
        super(wrapper);
        // this.enablePremium(super.getPurchasePremiumURL());
    }

    // public static List<String[]> getPluginDomains() {
    // final List<String[]> ret = new ArrayList<String[]>();
    // // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
    // ret.add(new String[] { "imgdew.com" });
    // return ret;
    // }
    //
    // public static String[] getAnnotationNames() {
    // return buildAnnotationNames(getPluginDomains());
    // }
    //
    // @Override
    // public String[] siteSupportedNames() {
    // return buildSupportedNames(getPluginDomains());
    // }
    //
    // public static String[] getAnnotationUrls() {
    // return XFileSharingProBasic.buildAnnotationUrls(getPluginDomains());
    // }
    // @Override
    // public String rewriteHost(final String host) {
    // return this.rewriteHost(getPluginDomains(), host);
    // }
    public static final String getDefaultAnnotationPatternPart() {
        return "/(?:embed-)?[a-z0-9]{12}(?:/[^/]+(?:\\.html)?)?";
    }

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + XFileSharingProBasic.getDefaultAnnotationPatternPart());
        }
        return ret.toArray(new String[0]);
    }

    /* Used variables */
    public String                 correctedBR                              = "";
    protected String              fuid                                     = null;
    /* don't touch the following! */
    private static AtomicInteger  freeRunning                              = new AtomicInteger(0);
    private static final String   PROPERTY_pw_required                     = "password_requested_by_website";
    protected static final String PROPERTY_captcha_required                = "captcha_requested_by_website";
    protected static final String PROPERTY_ACCOUNT_apikey                  = "apikey";
    private static final String   PROPERTY_PLUGIN_api_domain_with_protocol = "apidomain";

    /**
     * DEV NOTES XfileSharingProBasic Version 4.4.3.8<br />
     * mods: See overridden functions<br />
     * See official changelogs for upcoming XFS changes: https://sibsoft.net/xfilesharing/changelog.html |
     * https://sibsoft.net/xvideosharing/changelog.html <br/>
     * limit-info:<br />
     * captchatype-info: null 4dignum solvemedia reCaptchaV2<br />
     * Last compatible XFileSharingProBasic template: Version 2.7.8.7 in revision 40351 other:<br />
     */
    @Override
    public String getAGBLink() {
        return this.getMainPage() + "/tos.html";
    }

    public String getPurchasePremiumURL() {
        return this.getMainPage() + "/premium.html";
    }

    /**
     * Returns whether resume is supported or not for current download mode based on account availability and account type. <br />
     * Override this function to set resume settings!
     */
    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return true;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return false;
        }
    }

    /**
     * Returns how many max. chunks per file are allowed for current download mode based on account availability and account type. <br />
     * Override this function to set chunks settings!
     */
    public int getMaxChunks(final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return 1;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 1;
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        int max = getMaxSimultaneousFreeAnonymousDownloads();
        if (max < 0) {
            max = 20;
        }
        final int running = freeRunning.get();
        final int ret = Math.min(running + 1, max);
        return ret;
    }

    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return 1;
    }

    public int getMaxSimultaneousFreeAccountDownloads() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    /** Returns direct-link-property-String for current download mode based on account availibility and account type. */
    protected static String getDownloadModeDirectlinkProperty(final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return "freelink2";
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return "premlink";
        } else {
            /* Free(anonymous) and unknown account type */
            return "freelink";
        }
    }

    /**
     * @return true: Website supports https and plugin will prefer https. <br />
     *         false: Website does not support https - plugin will avoid https. <br />
     *         default: true
     */
    protected boolean supports_https() {
        final Class<? extends XFSConfigVideo> cfgO = this.getConfigInterface();
        if (cfgO != null) {
            return !PluginJsonConfig.get(cfgO).isPreferHTTP();
        } else {
            return true;
        }
    }

    /**
     * Relevant for premium accounts.
     *
     * @return A list of possible 'paymentURLs' which may contain an exact premium account expire-date down to the second. Return null to
     *         disable this feature!
     */
    protected String[] supports_precise_expire_date() {
        return new String[] { "/?op=payments", "/upgrade" };
    }

    /**
     * 2019-05-21: This old method is rarely supported in new XFS versions - you will usually not need this! <br />
     * <b> Enabling this will perform at least one additional http-request! </b> <br />
     * Enable this only for websites using <a href="https://sibsoft.net/xvideosharing.html">XVideosharing</a>. <br />
     * Demo-Website: <a href="http://xvideosharing.com">xvideosharing.com</a> <br />
     * Example-Host: <a href="http://clipsage.com">clipsage.com</a>
     *
     * @return true: Try to find final downloadlink via '/vidembed-<fuid>' request. <br />
     *         false: Skips this part. <br />
     *         default: false
     */
    @Deprecated
    protected boolean isVideohosterDirect() {
        return false;
    }

    /**
     * <b> Enabling this leads to at least one additional http-request! </b> <br />
     * Enable this for websites using <a href="https://sibsoft.net/xvideosharing.html">XVideosharing</a>. <br />
     * Demo-Website: <a href="http://xvideosharing.com">xvideosharing.com</a> DO NOT CALL THIS DIRECTLY - ALWAYS USE
     * internal_isVideohosterEmbed()!!!<br />
     *
     * @return true: Try to find final downloadlink via '/embed-<fuid>.html' request. <br />
     *         false: Skips this part. <br />
     *         default: false
     */
    protected boolean isVideohosterEmbed() {
        return false;
    }

    /** Checks whether current html code contains embed code for current fuid which would indicate that we have a videohost. */
    protected boolean isVideohosterEmbedHTML() {
        return new Regex(correctedBR, "/embed-" + this.fuid + "\\.html").matches();
    }

    /**
     * Keep in mind: Most videohosters will allow embedding their videos thus a "video filename" should be enforced but they may also
     * sometimes NOT support embedding videos while a "video filename" should still be enforced - then this trigger might be useful! </br DO
     * NOT CALL THIS FUNCTION DIRECTLY! Use 'internal_isVideohoster_enforce_video_filename' instead!!
     *
     * @return true: Implies that the hoster only allows video-content to be uploaded. Enforces .mp4 extension for all URLs. Also sets
     *         mime-hint via CompiledFiletypeFilter.VideoExtensions.MP4. <br />
     *         false: Website is just a normal filehost and their filenames should contain the fileextension. <br />
     *         default: false
     */
    protected boolean isVideohoster_enforce_video_filename() {
        return false;
    }

    /**
     * Enable this for websites using <a href="https://sibsoft.net/ximagesharing.html">XImagesharing</a>. <br />
     * Demo-Website: <a href="http://ximagesharing.com">ximagesharing.com</a>
     *
     * @return true: Implies that the hoster only allows photo-content to be uploaded. Enabling this will make plugin try to find
     *         picture-downloadlinks. Also sets mime-hint via CompiledFiletypeFilter.ImageExtensions.JPG. <br />
     *         false: Website is just an usual filehost, use given fileextension if possible. <br />
     *         default: false
     */
    protected boolean isImagehoster() {
        return false;
    }

    /**
     * See also function getFilesizeViaAvailablecheckAlt! <br />
     * <b> Enabling this will eventually lead to at least one additional website-request! </b> <br/>
     * <b>DO NOT CALL THIS DIRECTLY, USE internal_supports_availablecheck_alt </b>
     *
     * @return true: Implies that website supports getFilesizeViaAvailablecheckAlt call as an alternative source for filesize-parsing.<br />
     *         false: Implies that website does NOT support getFilesizeViaAvailablecheckAlt. <br />
     *         default: true
     */
    protected boolean supports_availablecheck_alt() {
        return true;
    }

    /**
     * Only works when getFilesizeViaAvailablecheckAlt returns true! See getFilesizeViaAvailablecheckAlt!
     *
     * @return true: Implies that website supports getFilesizeViaAvailablecheckAlt call without Form-handling (one call less than usual) as
     *         an alternative source for filesize-parsing. <br />
     *         false: Implies that website does NOT support getFilesizeViaAvailablecheckAlt without Form-handling. <br />
     *         default: true
     */
    protected boolean supports_availablecheck_filesize_alt_fast() {
        return true;
    }

    /**
     * See also function getFilesizeViaAvailablecheckAlt!
     *
     * @return true: Website uses old version of getFilesizeViaAvailablecheckAlt. Old will be tried first, then new if it fails. <br />
     *         false: Website uses current version of getFilesizeViaAvailablecheckAlt - it will be used first and if it fails, old call will
     *         be tried. <br />
     *         2019-07-09: Do not override this anymore - this code will auto-detect this situation!<br/>
     *         default: false
     */
    @Deprecated
    protected boolean prefer_availablecheck_filesize_alt_type_old() {
        return false;
    }

    /**
     * See also function getFnameViaAbuseLink!<br />
     * <b> Enabling this will eventually lead to at least one additional website-request! </b> <br/>
     * DO NOT CALL THIS DIRECTLY - ALWAYS USE internal_supports_availablecheck_filename_abuse()!!!<br />
     *
     * @return true: Implies that website supports getFnameViaAbuseLink call as an alternative source for filename-parsing. <br />
     *         false: Implies that website does NOT support getFnameViaAbuseLink. <br />
     *         default: true
     */
    protected boolean supports_availablecheck_filename_abuse() {
        return true;
    }

    /**
     * @return true: Try to RegEx filesize from normal html code. If this fails due to static texts on a website or even fake information,
     *         all links of a filehost may just get displayed with the same/wrong filesize. <br />
     *         false: Do not RegEx filesize from normal html code. Plugin will still be able to find filesize if supports_availablecheck_alt
     *         or supports_availablecheck_alt_fast is enabled (=default)! <br />
     *         default: true
     */
    protected boolean supports_availablecheck_filesize_html() {
        return true;
    }

    /**
     * This is designed to find the filesize during availablecheck for videohosts - videohosts usually don't display the filesize anywhere!
     * <br />
     * CAUTION: Only set this to true if a filehost: <br />
     * 1. Allows users to embed videos via '/embed-<fuid>.html'. <br />
     * 2. Does not display a filesize anywhere inside html code or other calls where we do not have to do an http request on a directurl.
     * <br />
     * 3. Allows a lot of simultaneous connections. <br />
     * 4. Is FAST - if it is not fast, this will noticably slow down the linkchecking procedure! <br />
     * 5. Allows using a generated direct-URL at least two times.
     *
     * @return true: requestFileInformation will use '/embed' to do an additional offline-check and find the filesize. <br />
     *         false: Disable this.<br />
     *         default: false
     */
    protected boolean supports_availablecheck_filesize_via_embedded_video() {
        return false;
    }

    /**
     * A correct setting increases linkcheck-speed as unnecessary redirects will be avoided. <br />
     * Also in some cases, you may get 404 errors or redirects to other websites if this setting is not correct.
     *
     * @return true: Implies that website requires 'www.' in all URLs. <br />
     *         false: Implies that website does NOT require 'www.' in all URLs. <br />
     *         default: false
     */
    protected boolean requires_WWW() {
        return false;
    }

    /**
     * Implies that a host supports login via 'API Mod'[https://sibsoft.net/xfilesharing/mods/api.html] via one of these APIs:
     * https://xvideosharing.docs.apiary.io/ OR https://xfilesharingpro.docs.apiary.io/ <br />
     * Enabling this will do the following: </br>
     * - Change login process to accept apikey instead of username & password </br>
     * - Use API for single- and mass linkchecking </br>
     * - Enforce API usage on account downloads: Never download via website, does NOT fallback to website! </br>
     * Sadly, it seems like their linkcheck function often only works for self uploaded conent. </br>
     * API docs: https://xvideosharing.docs.apiary.io/#reference/file/file-info/get-info/check-file(s) <br />
     * 2019-08-20: Some XFS websites are supported via another API via play.google.com/store/apps/details?id=com.zeuscloudmanager --> This
     * has nothing to do with the official XFS API! </br>
     * Example: xvideosharing.com, clicknupload.co <br />
     * default: false
     */
    protected boolean enable_account_api_only_mode() {
        return false;
    }

    /** If needed, this can be used to enforce cookie login e.g. if an unsupported captcha type is required for login. */
    protected boolean requiresCookieLogin() {
        return false;
    }

    protected boolean allow_api_download_if_apikey_is_available(final Account account) {
        final boolean apikey_is_available = this.getAPIKeyFromAccount(account) != null;
        /* Enable this switch to be able to use this in dev mode. Default = off as we do not use the API by default! */
        final boolean allow_api_premium_download = false;
        return DebugMode.TRUE_IN_IDE_ELSE_FALSE && apikey_is_available && allow_api_premium_download;
    }

    protected boolean allow_api_availablecheck_in_premium_mode_if_apikey_is_available(final Account account) {
        final boolean apikey_is_available = this.getAPIKeyFromAccount(account) != null;
        /* Enable this switch to be able to use this in dev mode. Default = off as we do not use the API by default! */
        final boolean allow_api_availablecheck_in_premium_mode = false;
        return DebugMode.TRUE_IN_IDE_ELSE_FALSE && apikey_is_available && allow_api_availablecheck_in_premium_mode;
    }

    /**
     * If enabled, API will be used to import (public) files into users' account and download them from there. </br>
     * This may sometimes be the only way to download via API because until now (2019-10-31) the XFS API can only be used to download files
     * which the user itself uploaded (= files which are in his account). </br>
     * Warning! The imported files may be PUBLIC as well by default! </br>
     * So far this exists for development purposes ONLY!!
     */
    protected boolean requires_api_getdllink_clone_workaround(final Account account) {
        /* Enable this switch to be able to use this in dev mode. Default = off as we do not use this workaround by default! */
        final boolean allow_dllink_clone_workaround = false;
        return DebugMode.TRUE_IN_IDE_ELSE_FALSE && allow_dllink_clone_workaround;
    }

    /**
     * 2019-08-20: Some websites' login will fail on the first attempt even with correct logindata. On the 2nd attempt a captcha will be
     * required and then the login should work. </br>
     * default = false
     */
    protected boolean allows_multiple_login_attempts_in_one_go() {
        return false;
    }

    /**
     * @return: Skip pre-download waittime or not. See waitTime function below. <br />
     *          default: false <br />
     *          example true: uploadrar.com
     */
    protected boolean preDownloadWaittimeSkippable() {
        return false;
    }

    /**
     * This is especially useful if a website e.g. provides URLs in this style by default:
     * https://website.com/[a-z0-9]{12}/filename.ext.html --> Then we already have the filename which is perfect as the website mass
     * linkchecker will only return online status and filesize.
     *
     * @default false
     */
    protected boolean supports_mass_linkcheck_over_website() {
        return false;
    }

    @Override
    public String getLinkID(DownloadLink link) {
        final String fuid = this.fuid != null ? this.fuid : getFUIDFromURL(link);
        if (fuid != null) {
            return getHost() + "://" + fuid;
        } else {
            return super.getLinkID(link);
        }
    }

    protected boolean isEmbedURL(final DownloadLink link) {
        return link.getPluginPatternMatcher().matches("https?://[A-Za-z0-9\\-\\.:]+/embed-[a-z0-9]{12}.*");
    }

    protected String buildEmbedURLPath(final String fuid) {
        return "/embed-" + fuid + ".html";
    }

    protected String buildNormalURLPath(final String fuid) {
        return "/" + fuid;
    }

    /**
     * Returns the desired host. Override is required in some cases where given host can contain unwanted subdomains e.g. imagetwist.com.
     */
    protected String getCorrectHost(final DownloadLink link, URL url) {
        return url.getHost();
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        final String fuid = this.fuid != null ? this.fuid : getFUIDFromURL(link);
        if (fuid != null && link.getPluginPatternMatcher() != null) {
            /* link cleanup, prefer https if possible */
            try {
                final URL url = new URL(link.getPluginPatternMatcher());
                final String urlHost = getCorrectHost(link, url);
                if (isEmbedURL(link)) {
                    /*
                     * URL displayed to the user. We correct this as we do not catch the ".html" part but we don't care about the host
                     * inside this URL!
                     */
                    link.setContentUrl(url.getProtocol() + "://" + urlHost + buildEmbedURLPath(fuid));
                }
                final String protocolCorrected;
                if (this.supports_https()) {
                    protocolCorrected = "https://";
                } else {
                    protocolCorrected = "http://";
                }
                /* Get full host with subdomain and correct base domain. */
                final String pluginHost = this.getHost();
                String hostCorrected;
                if (StringUtils.equalsIgnoreCase(urlHost, pluginHost)) {
                    /* E.g. down.example.com -> down.example.com */
                    hostCorrected = urlHost;
                } else {
                    /* e.g. down.xx.com -> down.yy.com, keep subdomain(s) */
                    hostCorrected = urlHost.replaceFirst("(?i)" + Pattern.quote(Browser.getHost(url, false)) + "$", pluginHost);
                }
                final String subDomain = Browser.getSubdomain(new URL("http://" + hostCorrected), true);
                if (requires_WWW() && subDomain == null) {
                    // only append www when no other subDomain is set
                    hostCorrected = "www." + hostCorrected;
                }
                link.setPluginPatternMatcher(protocolCorrected + hostCorrected + buildNormalURLPath(fuid));
            } catch (final MalformedURLException e) {
                logger.log(e);
            }
        }
    }

    @Override
    public Browser prepBrowser(final Browser prepBr, final String host) {
        if (!(this.browserPrepped.containsKey(prepBr) && this.browserPrepped.get(prepBr) == Boolean.TRUE)) {
            super.prepBrowser(prepBr, host);
            /* define custom browser headers and language settings */
            prepBr.setCookie(getMainPage(), "lang", "english");
            prepBr.setAllowedResponseCodes(new int[] { 500 });
        }
        return prepBr;
    }

    /**
     * Returns https?://host.tld ATTENTION: On override, make sure that current browsers' host still gets preferred over plugin host. </br>
     * If a subdomain is required, do not use this method before making a browser request!!
     */
    protected String getMainPage() {
        final String host;
        final String browser_host = this.br != null ? br.getHost(true) : null;
        if (browser_host != null) {
            /* Has a browser request been done before? Use this domain as it could e.g. differ from the plugin set main domain. */
            host = browser_host;
        } else {
            /* Return current main domain */
            /* 2019-07-25: This may not be correct out of the box e.g. for imgmaze.com */
            host = this.getHost();
        }
        final String protocol;
        if (this.supports_https()) {
            protocol = "https://";
        } else {
            protocol = "http://";
        }
        final String mainpage;
        String subDomain = null;
        try {
            subDomain = Browser.getSubdomain(new URL("http://" + host), true);
        } catch (MalformedURLException e) {
            logger.log(e);
        }
        if (requires_WWW() && subDomain == null) {
            // only append www when no other subDomain is set
            mainpage = protocol + "www." + host;
        } else {
            mainpage = protocol + host;
        }
        return mainpage;
    }

    /**
     * @return true: Link is password protected <br />
     *         false: Link is not password protected
     */
    public boolean isPasswordProtectedHTML(Form pwForm) {
        return new Regex(correctedBR, "<br>\\s*<b>\\s*Passwor(d|t)\\s*:\\s*</b>\\s*(<input|</div)").matches();
    }

    /**
     * Checks premiumonly status via current Browser-URL.
     *
     * @return true: Link only downloadable for premium users (sometimes also for registered users). <br />
     *         false: Link is downloadable for all users.
     */
    private boolean isPremiumOnlyURL() {
        return br.getURL() != null && br.getURL().contains("/?op=login&redirect=");
    }

    /**
     * Checks premiumonly status via current Browser-HTML AND URL via isPremiumOnlyURL.
     *
     * @return true: Link only downloadable for premium users (sometimes also for registered users). <br />
     *         false: Link is downloadable for all users.
     */
    public boolean isPremiumOnly() {
        final boolean premiumonly_by_url = isPremiumOnlyURL();
        final boolean premiumonly_filehost = new Regex(correctedBR, "( can download files up to |>\\s*Upgrade your account to download (?:larger|bigger) files|>\\s*The file you requested reached max downloads limit for Free Users|Please Buy Premium To download this file\\s*<|>\\s*This file reached max downloads limit|>\\s*This file is available for Premium Users only|>\\s*Available Only for Premium Members|>\\s*File is available only for Premium users|>\\s*This file can be downloaded by)").matches();
        /* 2019-05-30: Example: xvideosharing.com */
        final boolean premiumonly_videohost = new Regex(correctedBR, ">\\s*This video is available for Premium users only").matches();
        return premiumonly_by_url || premiumonly_filehost || premiumonly_videohost;
    }

    /**
     * @return true: Website is in maintenance mode - downloads are not possible but linkcheck may be possible. <br />
     *         false: Website is not in maintenance mode and should usually work fine.
     */
    protected boolean isWebsiteUnderMaintenance() {
        return br.getHttpConnection().getResponseCode() == 500 || new Regex(correctedBR, "\">\\s*This server is in maintenance mode").matches();
    }

    protected boolean isOffline(final DownloadLink link) {
        /* 2020-12-11:e.g. "video you are looking for is not found": dood.to | doodstream.com */
        return br.getHttpConnection().getResponseCode() == 404 || new Regex(correctedBR, "(No such file|>\\s*File Not Found\\s*<|>\\s*The file was removed by|Reason for deletion:\n|File Not Found|>\\s*The file expired|>\\s*File could not be found due to expiration or removal by the file owner|>\\s*The file of the above link no longer exists|>\\s*video you are looking for is not found)").matches();
    }

    @Override
    public boolean checkLinks(final DownloadLink[] urls) {
        final String apiKey = this.getAPIKey();
        if ((isAPIKey(apiKey) && this.supports_mass_linkcheck_over_api()) || enable_account_api_only_mode()) {
            return massLinkcheckerAPI(urls, apiKey, true);
        } else if (supports_mass_linkcheck_over_website()) {
            return this.massLinkcheckerWebsite(urls, true);
        } else {
            /* No mass linkchecking possible */
            return false;
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        final String apikey = getAPIKey();
        if (this.supports_single_linkcheck_over_api() && apikey != null) {
            /* API linkcheck */
            return this.requestFileInformationAPI(link, apikey);
        } else {
            /* Website linkcheck */
            return requestFileInformationWebsite(link, null, false);
        }
    }

    public AvailableStatus requestFileInformationWebsite(final DownloadLink link, final Account account, final boolean downloadsStarted) throws Exception {
        final String[] fileInfo = internal_getFileInfoArray();
        Browser altbr = null;
        fuid = null;
        correctDownloadLink(link);
        /* First, set fallback-filename */
        if (!link.isNameSet()) {
            setWeakFilename(link);
        }
        getPage(link.getPluginPatternMatcher());
        if (isOffline(link)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        setFUID(link);
        final String fallback_filename = this.getFallbackFilename(link);
        altbr = br.cloneBrowser();
        if (isPremiumOnlyURL()) {
            /*
             * Hosts whose urls are all premiumonly usually don't display any information about the URL at all - only maybe online/ofline.
             * There are 2 alternative ways to get this information anyways!
             */
            logger.info("PREMIUMONLY linkcheck: Trying alternative linkcheck");
            /* Find filename */
            if (this.internal_supports_availablecheck_filename_abuse()) {
                fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
            }
            /* Find filesize */
            if (this.internal_supports_availablecheck_alt()) {
                getFilesizeViaAvailablecheckAlt(altbr, link);
            }
        } else {
            /* Normal handling */
            scanInfo(fileInfo);
            {
                /* Two possible reasons to use fallback handling to find filename! */
                /*
                 * Filename abbreviated over x chars long (common serverside XFS bug) --> Use getFnameViaAbuseLink as a workaround to find
                 * the full-length filename!
                 */
                if (!StringUtils.isEmpty(fileInfo[0]) && fileInfo[0].trim().endsWith("&#133;") && this.internal_supports_availablecheck_filename_abuse()) {
                    logger.warning("filename length is larrrge");
                    fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
                } else if (StringUtils.isEmpty(fileInfo[0]) && this.internal_supports_availablecheck_filename_abuse()) {
                    /* We failed to find the filename via html --> Try getFnameViaAbuseLink as workaround */
                    logger.info("Failed to find filename, trying getFnameViaAbuseLink");
                    fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
                }
            }
            /* Filesize fallback */
            if (StringUtils.isEmpty(fileInfo[1]) && this.internal_supports_availablecheck_alt()) {
                /* Failed to find filesize? Try alternative way! */
                getFilesizeViaAvailablecheckAlt(altbr, link);
            }
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = fallback_filename;
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            /* This should never happen! Most likely the reason for this happening will be a developer mistake! */
            logger.warning("filename equals null --> Throwing \"plugin defect\"");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        /* Set md5hash - most times there is no md5hash available! */
        if (!StringUtils.isEmpty(fileInfo[2])) {
            link.setMD5Hash(fileInfo[2].trim());
        }
        {
            /* Correct- and set filename */
            /*
             * Decode HtmlEntity encoding in filename if needed.
             */
            if (Encoding.isHtmlEntityCoded(fileInfo[0])) {
                fileInfo[0] = Encoding.htmlDecode(fileInfo[0]);
            }
            /* Remove some html tags - in most cases not necessary! */
            fileInfo[0] = fileInfo[0].replaceAll("(</b>|<b>|\\.html)", "").trim();
            if (this.internal_isVideohoster_enforce_video_filename()) {
                /* For videohosts we often get ugly filenames such as 'some_videotitle.avi.mkv.mp4' --> Correct that! */
                fileInfo[0] = this.removeDoubleVideoExtensions(fileInfo[0], "mp4");
            }
            link.setName(fileInfo[0]);
        }
        {
            /* Set filesize */
            if (!StringUtils.isEmpty(fileInfo[1])) {
                link.setDownloadSize(SizeFormatter.getSize(fileInfo[1]));
            } else if (this.internal_isVideohosterEmbed() && supports_availablecheck_filesize_via_embedded_video() && !downloadsStarted) {
                /*
                 * Special case for some videohosts to determinethe filesize: Last chance to find filesize - do NOT execute this when used
                 * has started the download of our current DownloadLink as this could lead to "Too many connections" errors!
                 */
                requestFileInformationVideoEmbed(link, account, true);
            }
        }
        return AvailableStatus.TRUE;
    }

    /**
     * 2019-05-15: This can check availability via '/embed' URL. <br />
     * Only call this if internal_isVideohosterEmbed returns true. </br>
     *
     * @return final downloadurl
     */
    protected String requestFileInformationVideoEmbed(final DownloadLink link, final Account account, final boolean findFilesize) throws Exception {
        /*
         * Some video sites contain their directurl right on the first page - let's use this as an indicator and assume that the file is
         * online if we find a directurl. This also speeds-up linkchecking! Example: uqload.com
         */
        String dllink = getDllink(link, account, br, correctedBR);
        final Browser brc = this.br.cloneBrowser();
        if (StringUtils.isEmpty(dllink)) {
            if (brc.getURL() != null && !brc.getURL().contains("/embed")) {
                final String embed_access = getMainPage() + "/embed-" + fuid + ".html";
                getPage(brc, embed_access);
                /**
                 * 2019-07-03: Example response when embedding is not possible (deactivated or it is not a video-file): "Can't create video
                 * code" OR "Video embed restricted for this user"
                 */
            }
            /*
             * Important: Do NOT use 404 as offline-indicator here as the website-owner could have simply disabled embedding while it was
             * enabled before --> This would return 404 for all '/embed' URLs! Only rely on precise errormessages!
             */
            if (brc.toString().equalsIgnoreCase("File was deleted")) {
                /* Should be valid for all XFS hosts e.g. speedvideo.net, uqload.com */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            dllink = getDllink(link, account, brc, brc.toString());
            // final String url_thumbnail = getVideoThumbnailURL(br.toString());
        }
        if (findFilesize && !StringUtils.isEmpty(dllink) && !dllink.contains(".m3u8")) {
            /* Get- and set filesize from directurl */
            final boolean dllink_is_valid = checkDirectLinkAndSetFilesize(link, dllink, true) != null;
            /* Store directurl if it is valid */
            if (dllink_is_valid) {
                storeDirecturl(link, account, dllink);
            }
        }
        return dllink;
    }

    /**
     * Tries to find filename, filesize and md5hash inside html. On Override, make sure to first use your special RegExes e.g.
     * fileInfo[0]="bla", THEN, if needed, call super.scanInfo(fileInfo). <br />
     * fileInfo[0] = filename, fileInfo[1] = filesize, fileInfo[2] = md5hash (rarely used, 2019-05-21: e.g. md5 hash available and special
     * case: filespace.com)
     */
    public String[] scanInfo(final String[] fileInfo) {
        /*
         * 2019-04-17: TODO: Improve sharebox RegExes (also check if we can remove/improve sharebox0 and sharebox1 RegExes) as this may save
         * us from having to use other time-comsuming fallbacks such as getFilesizeViaAvailablecheckAlt or getFnameViaAbuseLink. E.g. new
         * XFS often has good information in their shareboxes!
         */
        final String sharebox0 = "copy\\(this\\);.+>(.+) - ([\\d\\.]+ (?:B|KB|MB|GB))</a></textarea>[\r\n\t ]+</div>";
        final String sharebox1 = "copy\\(this\\);.+\\](.+) - ([\\d\\.]+ (?:B|KB|MB|GB))\\[/URL\\]";
        /* 2019-05-08: 'Forum Code': Sharebox with filename & filesize (bytes), example: brupload.net, qtyfiles.com */
        final String sharebox2 = "\\[URL=https?://(?:www\\.)?[^/\"]+/" + this.fuid + "[^\\]]*?\\]([^\"/]*?)\\s*\\-\\s*(\\d+)\\[/URL\\]";
        /* First found for pixroute.com URLs */
        final String sharebox2_without_filesize = "\\[URL=https?://(?:www\\.)?[^/\"]+/" + this.fuid + "/([^<>\"/\\]]*?)(?:\\.html)?\\]";
        /*
         * 2019-05-21: E.g. uqload.com, vidoba.net - this method will return a 'cleaner' filename than in other places - their titles will
         * often end with " mp4" which we have to correct later!
         */
        final String sharebox3_videohost = "\\[URL=https?://[^/]+/" + this.fuid + "[^/<>\\]]*?\\]\\[IMG\\][^<>\"\\[\\]]+\\[/IMG\\]([^<>\"]+)\\[/URL\\]";
        /* standard traits from base page */
        if (StringUtils.isEmpty(fileInfo[0])) {
            /* 2019-06-12: TODO: Update this RegEx for e.g. up-4ever.org */
            fileInfo[0] = new Regex(correctedBR, "You have requested.*?https?://(?:www\\.)?[^/]+/" + fuid + "/([^<>\"]+)<").getMatch(0);
            if (StringUtils.isEmpty(fileInfo[0])) {
                /* 2019-05-21: E.g. datoporn.co */
                fileInfo[0] = new Regex(correctedBR, "name=\"fname\" (?:type=\"hidden\" )?value=\"(.*?)\"").getMatch(0);
                if (StringUtils.isEmpty(correctedBR)) {
                    fileInfo[0] = new Regex(correctedBR, "<h2>Download File(.*?)</h2>").getMatch(0);
                    /* traits from download1 page below */
                    if (StringUtils.isEmpty(fileInfo[0])) {
                        fileInfo[0] = new Regex(correctedBR, "Filename:?\\s*(<[^>]+>\\s*)+?([^<>\"]+)").getMatch(1);
                    }
                }
            }
        }
        /* Next - RegExes for specified types of websites e.g. imagehosts */
        if (StringUtils.isEmpty(fileInfo[0]) && this.isImagehoster()) {
            fileInfo[0] = regexImagehosterFilename(correctedBR);
        }
        /* Next - details from sharing boxes (new RegExes to old) */
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, sharebox2).getMatch(0);
            if (StringUtils.isEmpty(fileInfo[0])) {
                fileInfo[0] = new Regex(correctedBR, sharebox2_without_filesize).getMatch(0);
            }
            if (StringUtils.isEmpty(fileInfo[0])) {
                fileInfo[0] = new Regex(correctedBR, sharebox1).getMatch(0);
                if (StringUtils.isEmpty(fileInfo[0])) {
                    fileInfo[0] = new Regex(correctedBR, sharebox0).getMatch(0);
                }
                if (StringUtils.isEmpty(fileInfo[0])) {
                    /* Link of the box without filesize */
                    fileInfo[0] = new Regex(correctedBR, "onFocus=\"copy\\(this\\);\">https?://(?:www\\.)?[^/]+/" + fuid + "/([^<>\"]*?)</textarea").getMatch(0);
                }
            }
        }
        /* Next - RegExes for videohosts */
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, sharebox3_videohost).getMatch(0);
            if (StringUtils.isEmpty(fileInfo[0])) {
                /* 2017-04-11: Typically for XVideoSharing sites */
                fileInfo[0] = new Regex(correctedBR, Pattern.compile("<title>Watch ([^<>\"]+)</title>", Pattern.CASE_INSENSITIVE)).getMatch(0);
            }
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, "class=\"dfilename\">([^<>\"]*?)<").getMatch(0);
        }
        if (internal_isVideohosterEmbed() && (StringUtils.isEmpty(fileInfo[0]) || StringUtils.equalsIgnoreCase("No title", fileInfo[0]))) {
            /* 2019-10-15: E.g. vidoza.net */
            final String curFileName = br.getRegex("var\\s*curFileName\\s*=\\s*\"(.*?)\"").getMatch(0);
            if (StringUtils.isNotEmpty(curFileName)) {
                fileInfo[0] = curFileName;
            }
        }
        /*
         * 2019-05-16: Experimental RegEx to find 'safe' filesize traits which can always be checked, regardless of the
         * 'supports_availablecheck_filesize_html' setting:
         */
        if (StringUtils.isEmpty(fileInfo[1])) {
            fileInfo[1] = new Regex(correctedBR, sharebox2).getMatch(1);
        }
        /* 2019-07-12: Example: Katfile.com */
        if (StringUtils.isEmpty(fileInfo[1])) {
            fileInfo[1] = new Regex(correctedBR, "id\\s*=\\s*\"fsize[^\"]*\"\\s*>\\s*([0-9\\.]+\\s*[MBTGK]+)\\s*<").getMatch(0);
        }
        if (StringUtils.isEmpty(fileInfo[1])) {
            /* 2019-07-12: Example: Katfile.com */
            fileInfo[1] = new Regex(correctedBR, "class\\s*=\\s*\"statd\"\\s*>\\s*size\\s*</span>\\s*<span>\\s*([0-9\\.]+\\s*[MBTGK]+)\\s*<").getMatch(0);
        }
        if (StringUtils.isEmpty(fileInfo[1])) {
            /* 2020-08-10: E.g. myqloud.org */
            try {
                fileInfo[1] = getDllinkViaOfficialVideoDownload(this.br.cloneBrowser(), null, null, true);
            } catch (final Throwable e) {
                /* This should never happen */
                e.printStackTrace();
            }
        }
        if (this.supports_availablecheck_filesize_html() && StringUtils.isEmpty(fileInfo[1])) {
            /** TODO: Clean this up */
            /* Starting from here - more unsafe attempts */
            if (StringUtils.isEmpty(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, "\\(([0-9]+ bytes)\\)").getMatch(0);
                if (StringUtils.isEmpty(fileInfo[1])) {
                    fileInfo[1] = new Regex(correctedBR, "</font>[ ]+\\(([^<>\"'/]+)\\)(.*?)</font>").getMatch(0);
                }
            }
            /* Next - unsafe details from sharing box */
            if (StringUtils.isEmpty(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, sharebox0).getMatch(1);
                if (StringUtils.isEmpty(fileInfo[1])) {
                    fileInfo[1] = new Regex(correctedBR, sharebox1).getMatch(1);
                }
            }
            /* Generic failover */
            if (StringUtils.isEmpty(fileInfo[1])) {
                // sync with YetiShareCore.scanInfo- Generic failover
                fileInfo[1] = new Regex(correctedBR, "(?:>\\s*|\\(\\s*|\"\\s*|\\[\\s*|\\s+)([0-9\\.]+(?:\\s+|\\&nbsp;)?(TB|GB|MB|KB)(?!ps|/s|\\s*Storage|\\s*Disk|\\s*Space))").getMatch(0);
            }
        }
        /* MD5 is only available in very very rare cases! */
        if (StringUtils.isEmpty(fileInfo[2])) {
            fileInfo[2] = new Regex(correctedBR, "<b>\\s*MD5.*?</b>.*?nowrap>\\s*(.*?)\\s*<").getMatch(0);
        }
        return fileInfo;
    }

    public AvailableStatus requestFileInformationWebsiteMassLinkcheckerSingle(final DownloadLink link, final boolean setWeakFilename) throws IOException, PluginException {
        massLinkcheckerWebsite(new DownloadLink[] { link }, setWeakFilename);
        if (!link.isAvailabilityStatusChecked()) {
            return AvailableStatus.UNCHECKED;
        }
        if (link.isAvailabilityStatusChecked() && !link.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return AvailableStatus.TRUE;
    }

    /**
     * Use this to Override 'checkLinks(final DownloadLink[])' in supported plugins. <br />
     * Used by getFilesizeViaAvailablecheckAlt <br />
     * <b>Use this only if:</b> <br />
     * - You have verified that the filehost has a mass-linkchecker and it is working fine with this code. <br />
     * - The contentURLs contain a filename as a fallback e.g. https://host.tld/<fuid>/someFilename.png.html </br>
     * - If used for single URLs inside 'normal linkcheck' (e.g. inside requestFileInformation), call with setWeakFilename = false <br/>
     * - If the normal way via website is blocked somehow e.g. 'site-verification' captcha </br>
     * <b>- If used to check multiple URLs (mass-linkchecking feature), call with setWeakFilename = true!! </b>
     */
    public boolean massLinkcheckerWebsite(final DownloadLink[] urls, final boolean setWeakFilename) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        boolean linkcheckerHasFailed = false;
        String checkTypeCurrent = null;
        /* Checks linkchecking via: examplehost.com/?op=checkfiles AND examplehost.com/?op=check_files */
        final String checkTypeOld = "checkfiles";
        final String checkTypeNew = "check_files";
        final String checkType_last_used_and_working = getPluginConfig().getStringProperty("ALT_AVAILABLECHECK_LAST_WORKING", null);
        String checkURL = null;
        int linkcheckTypeTryCount = 0;
        try {
            final Browser br = new Browser();
            this.prepBrowser(br, getMainPage());
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            Form checkForm = null;
            while (true) {
                links.clear();
                while (true) {
                    /* We test max 50 links at once. 2020-05-28: Checked to up to 100 but let's use max. 50. */
                    if (index == urls.length || links.size() == 50) {
                        break;
                    } else {
                        links.add(urls[index]);
                        index++;
                    }
                }
                sb.delete(0, sb.capacity());
                for (final DownloadLink dl : links) {
                    sb.append(URLEncode.encodeURIComponent(dl.getPluginPatternMatcher()));
                    sb.append("%0A");
                }
                {
                    /* Check if the mass-linkchecker works and which check we have to use */
                    while (linkcheckTypeTryCount <= 1) {
                        if (checkTypeCurrent != null) {
                            /* No matter which checkType we tried first - it failed and we need to try the other one! */
                            if (checkTypeCurrent.equals(checkTypeNew)) {
                                checkTypeCurrent = checkTypeOld;
                            } else {
                                checkTypeCurrent = checkTypeNew;
                            }
                        } else if (this.prefer_availablecheck_filesize_alt_type_old()) {
                            /* Old checkType forced? */
                            checkTypeCurrent = checkTypeOld;
                        } else if (checkType_last_used_and_working != null) {
                            /* Try to re-use last working method */
                            checkTypeCurrent = checkType_last_used_and_working;
                        } else {
                            /* First launch */
                            checkTypeCurrent = checkTypeNew;
                        }
                        /*
                         * Sending the Form without a previous request might e.g. fail if the website requires "www." but
                         * supports_availablecheck_filesize_alt_fast returns false.
                         */
                        if (br.getURL() != null) {
                            checkURL = "/?op=" + checkTypeCurrent;
                        } else {
                            checkURL = getMainPage() + "/?op=" + checkTypeCurrent;
                        }
                        /* Get and prepare Form */
                        if (this.supports_availablecheck_filesize_alt_fast()) {
                            /* Quick way - we do not access the page before and do not need to parse the Form. */
                            checkForm = new Form();
                            checkForm.setMethod(MethodType.POST);
                            checkForm.setAction(checkURL);
                            checkForm.put("op", checkTypeCurrent);
                            checkForm.put("process", "Check+URLs");
                        } else {
                            /* Try to get the Form IF NEEDED as it can contain tokens which would otherwise be missing. */
                            getPage(br, checkURL);
                            checkForm = br.getFormByInputFieldKeyValue("op", checkTypeCurrent);
                            if (checkForm == null) {
                                /* TODO: Add auto-retry so that 2nd type of linkchecker is either used directly or on the next attempt! */
                                logger.info("Failed to find Form for checkType: " + checkTypeCurrent);
                                linkcheckTypeTryCount++;
                                continue;
                            }
                        }
                        checkForm.put("list", sb.toString());
                        this.submitForm(br, checkForm);
                        /*
                         * Some hosts will not display any errorpage but also we will not be able to find any of our checked file-IDs inside
                         * the html --> Use this to find out about non-working linkchecking method!
                         */
                        final String example_fuid = this.getFUIDFromURL(links.get(0));
                        if (br.getHttpConnection().getResponseCode() == 404 || !br.getURL().contains(checkTypeCurrent) || !br.containsHTML(example_fuid)) {
                            /*
                             * This method of linkcheck is not supported - increase the counter by one to find out if ANY method worked in
                             * the end.
                             */
                            logger.info("Failed to find check_files Status via checkType: " + checkTypeCurrent);
                            linkcheckTypeTryCount++;
                            continue;
                        } else {
                            break;
                        }
                    }
                }
                for (final DownloadLink dl : links) {
                    final String fuid = this.getFUIDFromURL(dl);
                    boolean isNewLinkchecker = true;
                    String html_for_fuid = br.getRegex("<tr>((?!</?tr>).)*?" + fuid + "((?!</?tr>).)*?</tr>").getMatch(-1);
                    if (html_for_fuid == null) {
                        /*
                         * 2019-07-10: E.g. for old linkcheckers which only return online/offline status in a single line and not as a html
                         * table.
                         */
                        html_for_fuid = br.getRegex("<font color=\\'(?:green|red)\\'>[^>]*?" + fuid + "[^>]*?</font>").getMatch(-1);
                        isNewLinkchecker = false;
                    }
                    if (html_for_fuid == null) {
                        logger.warning("Failed to find html_for_fuid --> Possible linkchecker failure");
                        linkcheckerHasFailed = true;
                        dl.setAvailableStatus(AvailableStatus.UNCHECKED);
                        continue;
                    }
                    final boolean isOffline;
                    if (isNewLinkchecker) {
                        isOffline = new Regex(html_for_fuid, "Not found").matches();
                    } else {
                        isOffline = new Regex(html_for_fuid, "<font color='red").matches();
                    }
                    if (isOffline) {
                        dl.setAvailable(false);
                    } else {
                        /* We know that the file is online - let's try to find the filesize ... */
                        dl.setAvailable(true);
                        try {
                            final String[] tabla_data = new Regex(html_for_fuid, "<td>?(.*?)</td>").getColumn(0);
                            final String size = tabla_data[2];
                            if (size != null) {
                                /*
                                 * Filesize should definitly be given - but at this stage we are quite sure that the file is online so let's
                                 * not throw a fatal error if the filesize cannot be found.
                                 */
                                dl.setDownloadSize(SizeFormatter.getSize(size));
                            }
                        } catch (final Throwable e) {
                        }
                    }
                    if (setWeakFilename && !dl.isNameSet()) {
                        /*
                         * We cannot get 'good' filenames via this call so we have to rely on our fallback-filenames (fuid or filename
                         * inside URL)!
                         */
                        setWeakFilename(dl);
                    }
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        } finally {
            if (linkcheckerHasFailed) {
                logger.info("Seems like checkfiles availablecheck is not supported by this host");
                this.getPluginConfig().setProperty("ALT_AVAILABLECHECK_LAST_FAILURE_TIMESTAMP", System.currentTimeMillis());
            } else {
                this.getPluginConfig().setProperty("ALT_AVAILABLECHECK_LAST_WORKING", checkTypeCurrent);
            }
        }
        if (linkcheckerHasFailed) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Try to find filename via '/?op=report_file&id=<fuid>'. Only call this function if internal_supports_availablecheck_filename_abuse()
     * returns true!<br />
     * E.g. needed if officially only logged in users can see filename or filename is missing in html code for whatever reason.<br />
     * Often needed for <b><u>IMAGEHOSTER</u> ' s</b>.<br />
     * Important: Only call this if <b><u>SUPPORTS_AVAILABLECHECK_ABUSE</u></b> is <b>true</b> (meaning omly try this if website supports
     * it)!<br />
     *
     * @throws Exception
     */
    protected String getFnameViaAbuseLink(final Browser br, final DownloadLink dl, final String fallbackFilename) throws Exception {
        getPage(br, getMainPage() + "/?op=report_file&id=" + fuid, false);
        /*
         * 2019-07-10: ONLY "No such file" as response might always be wrong and should be treated as a failure! Example: xvideosharing.com
         */
        final boolean fnameViaAbuseUnsupported = br.getHttpConnection().getResponseCode() == 404 || br.getHttpConnection().getResponseCode() == 500 || !br.getURL().contains("report_file") || br.toString().trim().equals("No such file");
        if (br.containsHTML(">No such file<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = regexFilenameAbuse(br);
        if (filename == null) {
            logger.info("Failed to find filename via report_file - using fallbackFilename");
            filename = fallbackFilename;
            if (fnameViaAbuseUnsupported) {
                logger.info("Seems like report_file availablecheck seems not to be supported by this host");
                this.getPluginConfig().setProperty("REPORT_FILE_AVAILABLECHECK_LAST_FAILURE_TIMESTAMP", System.currentTimeMillis());
            }
        } else {
            logger.info("Successfully found filename via report_file");
        }
        return filename;
    }

    /** Part of getFnameViaAbuseLink(). */
    public String regexFilenameAbuse(final Browser br) {
        String filename = null;
        final String filename_src = br.getRegex("<b>Filename\\s*:?\\s*<[^\n]+</td>").getMatch(-1);
        if (filename_src != null) {
            filename = new Regex(filename_src, ">([^>]+)</td>$").getMatch(0);
        }
        if (filename == null) {
            /* 2020-12-07 e.g. samaup.co, pandafiles.com */
            filename = br.getRegex("name=\"file_name\"[^>]*value=\"([^<>\"]+)\"").getMatch(0);
        }
        return filename;
    }

    /** Only use this if it is made sure that the host we're working with is an imagehoster (ximagesharing)!! */
    public String regexImagehosterFilename(final String source) {
        return new Regex(source, "class=\"pic\"[^>]*alt=\"([^<>\"]*?)\"").getMatch(0);
    }

    /**
     * Get filesize via massLinkchecker/alternative availablecheck.<br />
     * Wrapper for requestFileInformationWebsiteMassLinkcheckerSingle which contains a bit of extra log output </br>
     * Often used as fallback if o.g. officially only logged-in users can see filesize or filesize is not given in html code for whatever
     * reason.<br />
     * Often needed for <b><u>IMAGEHOSTER</u> ' s</b>.<br />
     * Important: Only call this if <b><u>supports_availablecheck_alt</u></b> is <b>true</b> (meaning omly try this if website supports
     * it)!<br />
     * Some older XFS versions AND videohosts have versions of this linkchecker which only return online/offline and NO FILESIZE!</br>
     * In case there is no filesize given, offline status will still be recognized! <br/>
     *
     * @return isOnline
     * @throws IOException
     */
    protected final boolean getFilesizeViaAvailablecheckAlt(final Browser br, final DownloadLink link) throws PluginException, IOException {
        logger.info("Trying getFilesizeViaAvailablecheckAlt");
        requestFileInformationWebsiteMassLinkcheckerSingle(link, false);
        final boolean isChecked = link.isAvailabilityStatusChecked();
        if (isChecked) {
            logger.info("Successfully checked URL via massLinkchecker | filesize: " + link.getView().getBytesTotal());
        } else {
            logger.info("Failed to find filesize");
        }
        return isChecked;
    }

    /**
     * Removes double extensions (of video hosts) to correct ugly filenames such as 'some_videoname.mkv.flv.mp4'.<br />
     *
     * @param filename
     *            input filename whose extensions will be replaced by desiredExtension.
     * @param desiredExtension
     *            Extension which is supposed to replace all eventually existing wrong extension(s). <br />
     *            If desiredExtension is null, this function will only remove existing extensions.
     */
    private String removeDoubleVideoExtensions(String filename, final String desiredExtension) {
        if (filename == null || desiredExtension == null) {
            return filename;
        }
        /* First let's remove all [XVideosharing] common video extensions */
        final VideoExtensions[] videoExtensions = VideoExtensions.values();
        boolean foundExt = true;
        while (foundExt) {
            foundExt = false;
            /* Check for video extensions at the end of the filename and remove them. Do this until no extension is found anymore */
            for (final VideoExtensions videoExt : videoExtensions) {
                final Pattern pattern = videoExt.getPattern();
                final String extStr = pattern.toString();
                final Pattern removePattern = Pattern.compile(".+(( |\\.)" + extStr + ")$", Pattern.CASE_INSENSITIVE);
                final String removeThis = new Regex(filename, removePattern).getMatch(0);
                if (removeThis != null) {
                    filename = filename.replace(removeThis, "");
                    foundExt = true;
                    break;
                }
            }
        }
        /* Add desired video extension if given. */
        if (desiredExtension != null && !filename.endsWith("." + desiredExtension)) {
            filename += "." + desiredExtension;
        }
        return filename;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformationWebsite(link, null, true);
        doFree(link, null);
    }

    /** Handles pre-download forms & captcha for free (anonymous) + FREE ACCOUNT modes. */
    public void doFree(final DownloadLink link, final Account account) throws Exception, PluginException {
        /* First bring up saved final links */
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        /*
         * 2019-05-21: TODO: Maybe try download right away instead of checking this here --> This could speed-up the
         * download-start-procedure!
         */
        String dllink = checkDirectLink(link, directlinkproperty);
        if (StringUtils.isEmpty(dllink)) {
            int download1counter = 0;
            final int download1max = 1;
            do {
                logger.info(String.format("Handling download1 loop %d / %d", download1counter + 1, download1max + 1));
                /**
                 * Try to find a downloadlink. Check different methods sorted from "usually available" to "rarely available" (e.g. there are
                 * a lot of sites which support video embedding).
                 */
                dllink = getDllinkViaOfficialVideoDownload(this.br.cloneBrowser(), link, account, false);
                /* Check for streaming/direct links on the first page. */
                if (StringUtils.isEmpty(dllink)) {
                    checkErrors(link, account, false);
                    dllink = getDllink(link, account);
                }
                /* Do they support standard video embedding? */
                if (StringUtils.isEmpty(dllink) && this.internal_isVideohosterEmbed()) {
                    try {
                        logger.info("Trying to get link via embed");
                        dllink = requestFileInformationVideoEmbed(link, account, false);
                        if (StringUtils.isEmpty(dllink)) {
                            logger.info("FAILED to get link via embed");
                        } else {
                            logger.info("Successfully found link via embed");
                        }
                    } catch (final InterruptedException e) {
                        throw e;
                    } catch (final Throwable e) {
                        logger.log(e);
                        logger.info("Failed to get link via embed");
                    }
                }
                /* Do they provide direct video URLs? */
                if (StringUtils.isEmpty(dllink) && this.isVideohosterDirect()) {
                    /* Legacy - most XFS videohosts do not support this anymore! */
                    try {
                        logger.info("Trying to get link via vidembed");
                        final Browser brv = br.cloneBrowser();
                        getPage(brv, "/vidembed-" + fuid, false);
                        dllink = brv.getRedirectLocation();
                        if (StringUtils.isEmpty(dllink)) {
                            logger.info("Failed to get link via vidembed because: " + br.toString());
                        } else {
                            logger.info("Successfully found link via vidembed");
                        }
                    } catch (final InterruptedException e) {
                        throw e;
                    } catch (final Throwable e) {
                        logger.log(e);
                        logger.info("Failed to get link via vidembed");
                    }
                }
                /* Do we have an imagehost? */
                if (StringUtils.isEmpty(dllink) && this.isImagehoster()) {
                    checkErrors(link, account, false);
                    int counter = 0;
                    final int countermax = 3;
                    Form imghost_next_form = null;
                    do {
                        logger.info(String.format("imghost_next_form loop %d / %d", counter + 1, countermax));
                        imghost_next_form = findImageForm(this.br);
                        if (imghost_next_form != null) {
                            /* end of backward compatibility */
                            submitForm(imghost_next_form);
                            checkErrors(link, account, false);
                            dllink = getDllink(link, account);
                            /* For imagehosts, filenames are often not given until we can actually see/download the image! */
                            final String image_filename = regexImagehosterFilename(correctedBR);
                            if (image_filename != null) {
                                link.setName(Encoding.htmlOnlyDecode(image_filename));
                            }
                        }
                        counter++;
                    } while (StringUtils.isEmpty(dllink) && imghost_next_form != null && counter < countermax);
                }
                /* Check for errors and download1 Form. Only execute this once! */
                if (StringUtils.isEmpty(dllink) && download1counter == 0) {
                    /*
                     * Check errors here because if we don't and a link is premiumonly, download1 Form will be present, plugin will send it
                     * and most likely end up with error "Fatal countdown error (countdown skipped)"
                     */
                    checkErrors(link, account, false);
                    final Form download1 = findFormDownload1Free();
                    if (download1 != null) {
                        logger.info("Found download1 Form");
                        submitForm(download1);
                        checkErrors(link, account, false);
                        dllink = getDllink(link, account);
                    } else {
                        logger.info("Failed to find download1 Form");
                    }
                }
                download1counter++;
            } while (download1counter <= download1max && dllink == null);
        }
        if (StringUtils.isEmpty(dllink)) {
            Form download2 = findFormDownload2Free();
            if (download2 == null) {
                /* Last chance - maybe our errorhandling kicks in here. */
                checkErrors(link, account, false);
                /* Okay we finally have no idea what happened ... */
                logger.warning("Failed to find download2 Form");
                checkErrorsLastResort(account);
            }
            logger.info("Found download2 Form");
            /*
             * E.g. html contains text which would lead to error ERROR_IP_BLOCKED --> We're not checking for it as there is a download Form
             * --> Then when submitting it, html will contain another error e.g. 'Skipped countdown' --> In this case we want to prefer the
             * first thrown Exception. Why do we not check errors before submitting download2 Form? Because html could contain faulty
             * errormessages!
             */
            Exception exceptionBeforeDownload2Submit = null;
            try {
                checkErrors(link, account, false);
            } catch (final Exception e) {
                logger.log(e);
                exceptionBeforeDownload2Submit = e;
                logger.info("Found Exception before download2 Form submit");
            }
            /* Define how many forms deep do we want to try? */
            final int download2start = 0;
            final int download2max = 2;
            for (int download2counter = download2start; download2counter <= download2max; download2counter++) {
                logger.info(String.format("Download2 loop %d / %d", download2counter + 1, download2max + 1));
                download2.remove(null);
                final long timeBefore = System.currentTimeMillis();
                handlePassword(download2, link);
                handleCaptcha(link, download2);
                /* 2019-02-08: MD5 can be on the subsequent pages - it is to be found very rare in current XFS versions */
                if (link.getMD5Hash() == null) {
                    final String md5hash = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
                    if (md5hash != null) {
                        link.setMD5Hash(md5hash.trim());
                    }
                }
                waitTime(link, timeBefore);
                final URLConnectionAdapter formCon = openAntiDDoSRequestConnection(br, br.createFormRequest(download2));
                if (isDownloadableContent(formCon)) {
                    /* Very rare case - e.g. tiny-files.com */
                    handleDownload(link, account, dllink, formCon.getRequest());
                    return;
                } else {
                    try {
                        br.followConnection(true);
                    } catch (final IOException e) {
                        logger.log(e);
                    }
                    this.correctBR();
                    try {
                        formCon.disconnect();
                    } catch (final Throwable e) {
                    }
                }
                logger.info("Submitted Form download2");
                checkErrors(link, account, true);
                /* 2020-03-02: E.g. akvideo.stream */
                dllink = getDllinkViaOfficialVideoDownload(this.br.cloneBrowser(), link, account, false);
                if (dllink == null) {
                    dllink = getDllink(link, account);
                }
                download2 = findFormDownload2Free();
                if (StringUtils.isEmpty(dllink) && (download2 != null || download2counter == download2max)) {
                    logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                    /* Check if maybe an error happened before stepping in download2 loop --> Throw that */
                    if (download2counter == download2start + 1 && exceptionBeforeDownload2Submit != null) {
                        logger.info("Throwing exceptionBeforeDownload2Submit");
                        throw exceptionBeforeDownload2Submit;
                    }
                    checkErrorsLastResort(account);
                } else if (StringUtils.isEmpty(dllink) && download2 != null) {
                    invalidateLastChallengeResponse();
                    continue;
                } else {
                    validateLastChallengeResponse();
                    break;
                }
            }
        }
        handleDownload(link, account, dllink, null);
    }

    /**
     * Checks if official video download is possible and returns final downloadurl if possible. </br>
     * This should NOT throw any Exceptions!
     *
     * @param returnFilesize
     *            true = Only return filesize of selected quality. Use this in availablecheck. </br>
     *            false = return final downloadurl of selected quality. Use this in download mode.
     */
    protected String getDllinkViaOfficialVideoDownload(final Browser brc, final DownloadLink link, final Account account, final boolean returnFilesize) throws Exception {
        if (returnFilesize) {
            logger.info("[FilesizeMode] Trying to find official video downloads");
        } else {
            logger.info("[DownloadMode] Trying to find official video downloads");
        }
        String dllink = null;
        /* Info in table. E.g. xvideosharing.com, watchvideo.us */
        String[] videoQualityHTMLs = new Regex(correctedBR, "<tr><td>[^\r\t\n]+download_video\\(.*?</td></tr>").getColumn(-1);
        if (videoQualityHTMLs.length == 0) {
            /* Match on line - safe attempt but this may not include filesize! */
            videoQualityHTMLs = new Regex(correctedBR, "download_video\\([^\r\t\n]+").getColumn(-1);
        }
        /*
         * Internal quality identifiers highest to lowest (inside 'download_video' String): o = original, h = high, n = normal, l=low
         */
        final HashMap<String, Integer> qualityMap = new HashMap<String, Integer>();
        qualityMap.put("l", 20); // low
        qualityMap.put("n", 40); // normal
        qualityMap.put("h", 60); // high
        qualityMap.put("o", 80); // original
        long maxInternalQualityValue = 0;
        String filesizeStr = null;
        String videoQualityStr = null;
        String videoHash = null;
        String targetHTML = null;
        final String userSelectedQualityValue = getPreferredDownloadQuality();
        boolean foundUserSelectedQuality = false;
        if (videoQualityHTMLs.length == 0) {
            logger.info("Failed to find any official video downloads");
        }
        if (userSelectedQualityValue == null) {
            logger.info("Trying to find highest quality for official video download");
        } else {
            logger.info(String.format("Trying to find user selected quality %s for official video download", userSelectedQualityValue));
        }
        int selectedQualityIndex = 0;
        for (int currentQualityIndex = 0; currentQualityIndex < videoQualityHTMLs.length; currentQualityIndex++) {
            final String videoQualityHTML = videoQualityHTMLs[currentQualityIndex];
            final String filesizeStrTmp = new Regex(videoQualityHTML, "(([0-9\\.]+)\\s*(KB|MB|GB|TB))").getMatch(0);
            // final String vid = videoinfo.getMatch(0);
            final Regex videoinfo = new Regex(videoQualityHTML, "download_video\\('([a-z0-9]+)','([^<>\"\\']*)','([^<>\"\\']*)'");
            // final String vid = videoinfo.getMatch(0);
            /* Usually this will be 'o' standing for "original quality" */
            final String videoQualityStrTmp = videoinfo.getMatch(1);
            final String videoHashTmp = videoinfo.getMatch(2);
            if (StringUtils.isEmpty(videoQualityStrTmp) || StringUtils.isEmpty(videoHashTmp)) {
                /*
                 * Possible plugin failure but let's skip bad items. Upper handling will fallback to stream download if everything fails!
                 */
                continue;
            } else if (!qualityMap.containsKey(videoQualityStrTmp)) {
                /*
                 * 2020-01-18: There shouldn't be any unknown values but we should consider allowing such in the future maybe as final
                 * fallback.
                 */
                logger.info("Skipping unknown quality: " + videoQualityStrTmp);
                continue;
            }
            if (userSelectedQualityValue != null && videoQualityStrTmp.equalsIgnoreCase(userSelectedQualityValue)) {
                logger.info("Found user selected quality: " + userSelectedQualityValue);
                foundUserSelectedQuality = true;
                videoQualityStr = videoQualityStrTmp;
                videoHash = videoHashTmp;
                if (filesizeStrTmp != null) {
                    /*
                     * Usually, filesize for official video downloads will be given but not in all cases. It may also happen that our upper
                     * RegEx fails e.g. for supervideo.tv.
                     */
                    filesizeStr = filesizeStrTmp;
                }
                targetHTML = videoQualityHTML;
                selectedQualityIndex = currentQualityIndex;
                break;
            } else {
                /* Look for best quality */
                final int internalQualityValueTmp = qualityMap.get(videoQualityStrTmp);
                if (internalQualityValueTmp < maxInternalQualityValue) {
                    /* Only continue with qualities that are higher than the highest we found so far. */
                    continue;
                }
                maxInternalQualityValue = internalQualityValueTmp;
                videoQualityStr = videoQualityStrTmp;
                videoHash = videoHashTmp;
                if (filesizeStrTmp != null) {
                    /*
                     * Usually, filesize for official video downloads will be given but not in all cases. It may also happen that our upper
                     * RegEx fails e.g. for supervideo.tv.
                     */
                    filesizeStr = filesizeStrTmp;
                }
                targetHTML = videoQualityHTML;
                selectedQualityIndex = currentQualityIndex;
            }
        }
        if (targetHTML == null || videoQualityStr == null || videoHash == null) {
            if (videoQualityHTMLs != null && videoQualityHTMLs.length > 0) {
                /* This should never happen */
                logger.info(String.format("Failed to find officially downloadable video quality although there are %d qualities available", videoQualityHTMLs.length));
            }
            return null;
        }
        if (filesizeStr == null) {
            /*
             * Last chance attempt to find filesize for selected quality. Only allow units "MB" and "GB" as most filesizes will have one of
             * these units.
             */
            final String[] filesizeCandidates = br.getRegex("(\\d+(?:\\.\\d{1,2})? *(MB|GB))").getColumn(0);
            /* Are there as many filesizes available as there are video qualities --> Chose correct filesize by index */
            if (filesizeCandidates.length == videoQualityHTMLs.length) {
                filesizeStr = filesizeCandidates[selectedQualityIndex];
            }
        }
        if (foundUserSelectedQuality) {
            logger.info("Found user selected quality: " + userSelectedQualityValue);
        } else {
            logger.info("Picked BEST quality: " + videoQualityStr);
        }
        if (filesizeStr == null) {
            /* No dramatic failure */
            logger.info("Failed to find filesize");
        } else {
            logger.info("Found filesize of official video download: " + filesizeStr);
        }
        if (returnFilesize) {
            /* E.g. in availablecheck */
            return filesizeStr;
        }
        try {
            /* 2019-08-29: Waittime here is possible but a rare case e.g. deltabit.co */
            this.waitTime(link, System.currentTimeMillis());
            logger.info("Waiting extra wait seconds: " + getDllinkViaOfficialVideoDownloadExtraWaittimeSeconds());
            this.sleep(getDllinkViaOfficialVideoDownloadExtraWaittimeSeconds() * 1000l, link);
            getPage(brc, "/dl?op=download_orig&id=" + this.getFUIDFromURL(link) + "&mode=" + videoQualityStr + "&hash=" + videoHash);
            /* 2019-08-29: This Form may sometimes be given e.g. deltabit.co */
            final Form download1 = brc.getFormByInputFieldKeyValue("op", "download1");
            if (download1 != null) {
                this.submitForm(brc, download1);
                /*
                 * 2019-08-29: TODO: A 'checkErrors' is supposed to be here but at the moment not possible if we do not use our 'standard'
                 * browser
                 */
            }
            /*
             * 2019-10-04: TODO: Unsure whether we should use the general 'getDllink' method here as it contains a lot of RegExes (e.g. for
             * streaming URLs) which are completely useless here.
             */
            dllink = this.getDllink(link, account, brc, brc.toString());
            if (StringUtils.isEmpty(dllink)) {
                /*
                 * 2019-05-30: Test - worked for: xvideosharing.com - not exactly required as getDllink will usually already return a
                 * result.
                 */
                dllink = new Regex(brc.toString(), "<a href=\"(https?[^\"]+)\"[^>]*>Direct Download Link</a>").getMatch(0);
            }
            if (StringUtils.isEmpty(dllink)) {
                logger.info("Failed to find final downloadurl");
            }
        } catch (final InterruptedException e) {
            throw e;
        } catch (final Throwable e) {
            logger.log(e);
            logger.warning("Official video download failed: Exception occured");
            /*
             * Continue via upper handling - usually videohosts will have streaming URLs available so a failure of this is not fatal for us.
             */
        }
        if (StringUtils.isEmpty(dllink)) {
            logger.warning("Failed to find dllink via official video download");
        } else {
            logger.info("Successfully found dllink via official video download");
        }
        return dllink;
    }

    /**
     * 2020-05-22: Workaround attempt for unnerving class="err">Security error< which can sometimes appear if you're too fast in this
     * handling. </br>
     * This issue may have solved in newer XFS versions so we might be able to remove this extra wait in the long run.
     */
    protected int getDllinkViaOfficialVideoDownloadExtraWaittimeSeconds() {
        return 5;
    }

    /**
     * @return User selected video download quality for official video download. </br>
     *         h = high </br>
     *         n = normal </br>
     *         l = low </br>
     *         null = No selection/Grab BEST available
     */
    private String getPreferredDownloadQuality() {
        final Class<? extends XFSConfigVideo> cfgO = this.getConfigInterface();
        if (cfgO != null) {
            final XFSConfigVideo cfg = PluginJsonConfig.get(cfgO);
            final PreferredDownloadQuality quality = cfg.getPreferredDownloadQuality();
            switch (quality) {
            default:
                return null;
            case BEST:
                return null;
            case HIGH:
                return "h";
            case NORMAL:
                return "n";
            case LOW:
                return "l";
            }
        } else {
            return null;
        }
    }

    protected void handleRecaptchaV2(final DownloadLink link, final Form captchaForm) throws Exception {
        /*
         * 2019-06-06: Most widespread case with an important design-flaw (see below)!
         */
        final CaptchaHelperHostPluginRecaptchaV2 rc2 = getCaptchaHelperHostPluginRecaptchaV2(this, br);
        logger.info("Detected captcha method \"RecaptchaV2\" normal-type '" + rc2.getType() + "' for this host");
        if (!this.preDownloadWaittimeSkippable()) {
            final String waitStr = regexWaittime();
            if (waitStr != null && waitStr.matches("\\d+")) {
                final int preDownloadWaittime = Integer.parseInt(waitStr) * 1001;
                final int reCaptchaV2Timeout = rc2.getSolutionTimeout();
                if (preDownloadWaittime > reCaptchaV2Timeout) {
                    /*
                     * Admins may sometimes setup waittimes that are higher than the reCaptchaV2 timeout so lets say they set up 180 seconds
                     * of pre-download-waittime --> User solves captcha immediately --> Captcha-solution times out after 120 seconds -->
                     * User has to re-enter it (and it would fail in JD)! If admins set it up in a way that users can solve the captcha via
                     * the waittime counts down, this failure may even happen via browser (example: xubster.com)! See workaround below!
                     */
                    /*
                     * This is basically a workaround which avoids running into reCaptchaV2 timeout: Make sure that we wait less than 120
                     * seconds after the user has solved the captcha. If the waittime is higher than 120 seconds, we'll wait two times:
                     * Before AND after the captcha!
                     */
                    final int prePrePreDownloadWait = preDownloadWaittime - reCaptchaV2Timeout;
                    logger.info("Waittime is higher than reCaptchaV2 timeout --> Waiting a part of it before solving captcha to avoid timeouts");
                    logger.info("Pre-pre download waittime seconds: " + (prePrePreDownloadWait / 1000));
                    this.sleep(prePrePreDownloadWait, link);
                }
            }
        }
        final String recaptchaV2Response = rc2.getToken();
        captchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
    }

    protected CaptchaHelperHostPluginRecaptchaV2 getCaptchaHelperHostPluginRecaptchaV2(PluginForHost plugin, Browser br) throws PluginException {
        return new CaptchaHelperHostPluginRecaptchaV2(this, br);
    }

    /** Handles all kinds of captchas, also login-captcha - fills the given captchaForm. */
    public void handleCaptcha(final DownloadLink link, final Form captchaForm) throws Exception {
        /* Captcha START */
        if (new Regex(correctedBR, Pattern.compile("\\$\\.post\\(\\s*\"/ddl\"", Pattern.CASE_INSENSITIVE)).matches()) {
            if (new Regex(correctedBR, "hcaptcha\\.com").matches()) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unsupported captcha type hcaptcha", 3 * 60 * 60 * 1000l);
            }
            /* 2019-06-06: Rare case */
            final CaptchaHelperHostPluginRecaptchaV2 rc2 = new CaptchaHelperHostPluginRecaptchaV2(this, br);
            logger.info("Detected captcha method \"RecaptchaV2\" special-type '" + rc2.getType() + "' for this host");
            final String recaptchaV2Response = rc2.getToken();
            /*
             * 2017-12-07: New - solve- and check reCaptchaV2 here via ajax call, then wait- and submit the main downloadform. This might as
             * well be a workaround by the XFS developers to avoid expiring reCaptchaV2 challenges. Example: filefox.cc
             */
            /* 2017-12-07: New - this case can only happen during download and cannot be part of the login process! */
            /* Do not put the result in the given Form as the check itself is handled via Ajax right here! */
            captchaForm.put("g-recaptcha-response", "");
            final Form ajaxCaptchaForm = new Form();
            ajaxCaptchaForm.setMethod(MethodType.POST);
            ajaxCaptchaForm.setAction("/ddl");
            final InputField inputField_Rand = captchaForm.getInputFieldByName("rand");
            final String file_id = PluginJSonUtils.getJson(br, "file_id");
            if (inputField_Rand != null) {
                /* This is usually given */
                ajaxCaptchaForm.put("rand", inputField_Rand.getValue());
            }
            if (!StringUtils.isEmpty(file_id)) {
                /* This is usually given */
                ajaxCaptchaForm.put("file_id", file_id);
            }
            ajaxCaptchaForm.put("op", "captcha1");
            ajaxCaptchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
            /* User existing Browser object as we get a cookie which is required later. */
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            this.submitForm(br, ajaxCaptchaForm);
            if (!br.toString().equalsIgnoreCase("OK")) {
                if (br.toString().equalsIgnoreCase("ERROR: Wrong captcha")) {
                    /* 2019-12-14: Happens but should never happen ... */
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                logger.warning("Fatal reCaptchaV2 ajax handling failure");
                checkErrorsLastResort(null);
            }
            br.getHeaders().remove("X-Requested-With");
            link.setProperty(PROPERTY_captcha_required, Boolean.TRUE);
        } else if (containsRecaptchaV2Class(correctedBR)) {
            handleRecaptchaV2(link, captchaForm);
            link.setProperty(PROPERTY_captcha_required, Boolean.TRUE);
        } else if (captchaForm.containsHTML("hcaptcha\\.com/") || captchaForm.containsHTML("class=\"h-captcha\"")) {
            /*
             * TODO: 2020-12-17: Automatically display cookie login dialog in this case if this unsupported captcha type is required during
             * login process.
             */
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Unsupported captcha type 'hcaptcha'");
        } else {
            if (StringUtils.containsIgnoreCase(correctedBR, ";background:#ccc;text-align")) {
                logger.info("Detected captcha method \"plaintext captchas\" for this host");
                /* Captcha method by ManiacMansion */
                String[][] letters = new Regex(br, "<span style='position:absolute;padding-left:(\\d+)px;padding-top:\\d+px;'>(&#\\d+;)</span>").getMatches();
                if (letters == null || letters.length == 0) {
                    /* Try again, this time look in non-cleaned-up html as correctBR() could have removed this part! */
                    letters = new Regex(br.toString(), "<span style='position:absolute;padding-left:(\\d+)px;padding-top:\\d+px;'>(&#\\d+;)</span>").getMatches();
                    if (letters == null || letters.length == 0) {
                        logger.warning("plaintext captchahandling broken!");
                        checkErrorsLastResort(null);
                    }
                }
                final SortedMap<Integer, String> capMap = new TreeMap<Integer, String>();
                for (String[] letter : letters) {
                    capMap.put(Integer.parseInt(letter[0]), Encoding.htmlDecode(letter[1]));
                }
                final StringBuilder code = new StringBuilder();
                for (String value : capMap.values()) {
                    code.append(value);
                }
                captchaForm.put("code", code.toString());
                logger.info("Put captchacode " + code.toString() + " obtained by captcha metod \"plaintext captchas\" in captchaForm");
                link.setProperty(PROPERTY_captcha_required, Boolean.TRUE);
            } else if (StringUtils.containsIgnoreCase(correctedBR, "/captchas/")) {
                logger.info("Detected captcha method \"Standard captcha\" for this host");
                final String[] sitelinks = HTMLParser.getHttpLinks(br.toString(), "");
                String captchaurl = null;
                if (sitelinks == null || sitelinks.length == 0) {
                    logger.warning("Standard captcha captchahandling broken!");
                    checkErrorsLastResort(null);
                }
                for (final String linkTmp : sitelinks) {
                    if (linkTmp.contains("/captchas/")) {
                        captchaurl = linkTmp;
                        break;
                    }
                }
                if (StringUtils.isEmpty(captchaurl)) {
                    /* Fallback e.g. for relative URLs (e.g. subyshare.com [bad example, needs special handling anways!]) */
                    captchaurl = new Regex(correctedBR, "(/captchas/[a-z0-9]+\\.jpg)").getMatch(0);
                }
                if (captchaurl == null) {
                    logger.warning("Standard captcha captchahandling broken2!");
                    checkErrorsLastResort(null);
                }
                String code = getCaptchaCode("xfilesharingprobasic", captchaurl, link);
                captchaForm.put("code", code);
                logger.info("Put captchacode " + code + " obtained by captcha metod \"Standard captcha\" in the form.");
                link.setProperty(PROPERTY_captcha_required, Boolean.TRUE);
            } else if (new Regex(correctedBR, "(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)").matches()) {
                logger.info("Detected captcha method \"reCaptchaV1\" for this host");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Website uses reCaptchaV1 which has been shut down by Google. Contact website owner!");
            } else if (new Regex(correctedBR, "solvemedia\\.com/papi/").matches()) {
                logger.info("Detected captcha method \"solvemedia\" for this host");
                final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia(br);
                File cf = null;
                try {
                    cf = sm.downloadCaptcha(getLocalCaptchaFile());
                } catch (final InterruptedException e) {
                    throw e;
                } catch (final Exception e) {
                    if (org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia.FAIL_CAUSE_CKEY_MISSING.equals(e.getMessage())) {
                        throw new PluginException(LinkStatus.ERROR_FATAL, "Host side solvemedia.com captcha error - please contact the " + this.getHost() + " support", -1, e);
                    } else {
                        throw e;
                    }
                }
                final String code = getCaptchaCode("solvemedia", cf, link);
                final String chid = sm.getChallenge(code);
                captchaForm.put("adcopy_challenge", chid);
                captchaForm.put("adcopy_response", "manual_challenge");
                link.setProperty(PROPERTY_captcha_required, Boolean.TRUE);
            } else if (br.containsHTML("id=\"capcode\" name= \"capcode\"")) {
                logger.info("Detected captcha method \"keycaptcha\"");
                String result = handleCaptchaChallenge(getDownloadLink(), new KeyCaptcha(this, br, getDownloadLink()).createChallenge(this));
                if (result == null) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                if ("CANCEL".equals(result)) {
                    throw new PluginException(LinkStatus.ERROR_FATAL);
                }
                captchaForm.put("capcode", result);
                link.setProperty(PROPERTY_captcha_required, Boolean.TRUE);
            } else {
                link.setProperty(PROPERTY_captcha_required, Boolean.FALSE);
            }
            /* Captcha END */
        }
    }

    /** Tries to find 1st download Form for free(and Free-Account) download. */
    public Form findFormDownload1Free() throws Exception {
        final Form download1 = br.getFormByInputFieldKeyValue("op", "download1");
        if (download1 != null) {
            download1.remove("method_premium");
            /* Fix/Add "method_free" value if necessary. */
            if (!download1.hasInputFieldByName("method_free") || download1.getInputFieldByName("method_free").getValue() == null) {
                String method_free_value = download1.getRegex("\"method_free\" value=\"([^<>\"]+)\"").getMatch(0);
                if (method_free_value == null || method_free_value.equals("")) {
                    method_free_value = "Free Download";
                }
                download1.put("method_free", Encoding.urlEncode(method_free_value));
            }
        }
        return download1;
    }

    /** Tries to find 2nd download Form for free(and Free-Account) download. */
    protected Form findFormDownload2Free() {
        Form dlForm = null;
        /* First try to find Form for video hosts with multiple qualities. */
        final Form[] forms = br.getForms();
        for (final Form form : forms) {
            final InputField op_field = form.getInputFieldByName("op");
            /* E.g. name="op" value="download_orig" */
            if (form.containsHTML("method_") && op_field != null && op_field.getValue().contains("download")) {
                dlForm = form;
                break;
            }
        }
        /* Nothing found? Fallback to simpler handling - this is more likely to pickup a wrong Form! */
        if (dlForm == null) {
            dlForm = br.getFormbyProperty("name", "F1");
        }
        if (dlForm == null) {
            dlForm = br.getFormByInputFieldKeyValue("op", "download2");
        }
        if (dlForm != null && dlForm.hasInputFieldByName("adblock_detected")) {
            final InputField adb = dlForm.getInputField("adblock_detected");
            if (StringUtils.isEmpty(adb.getValue())) {
                dlForm.removeInputField(adb);
                adb.setValue("0");
                dlForm.addInputField(adb);
            }
        }
        return dlForm;
    }

    /**
     * Tries to find download Form for premium download.
     *
     * @throws Exception
     */
    public Form findFormDownload2Premium() throws Exception {
        return br.getFormbyProperty("name", "F1");
    }

    /**
     * Check if a stored directlink exists under property 'property' and if so, check if it is still valid (leads to a downloadable content
     * [NOT html]).
     */
    protected final String checkDirectLink(final DownloadLink link, final String property) {
        final String dllink = link.getStringProperty(property);
        if (dllink != null) {
            final String ret = checkDirectLinkAndSetFilesize(link, dllink, false);
            if (ret != null) {
                return ret;
            } else {
                link.removeProperty(property);
                return null;
            }
        }
        return null;
    }

    /**
     * Checks if a directurl leads to downloadable content and if so, returns true. <br />
     * This will also return true if the serverside connection limit has been reached. <br />
     *
     * @param link
     *            : The DownloadLink
     * @param directurl
     *            : Directurl which should lead to downloadable content
     * @param setFilesize
     *            : true = setVerifiedFileSize filesize if directurl is really downloadable
     */
    protected final String checkDirectLinkAndSetFilesize(final DownloadLink link, final String directurl, final boolean setFilesize) {
        if (StringUtils.isEmpty(directurl) || !directurl.startsWith("http")) {
            return null;
        } else {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = openAntiDDoSRequestConnection(br2, br2.createHeadRequest(directurl));
                /* For video streams we often don't get a Content-Disposition header. */
                if (con.getResponseCode() == 503) {
                    try {
                        br2.followConnection(true);
                    } catch (IOException e) {
                        logger.log(e);
                    }
                    /* Ok */
                    /*
                     * Too many connections but that does not mean that our directlink is invalid. Accept it and if it still returns 503 on
                     * download-attempt this error will get displayed to the user - such directlinks should work again once there are less
                     * active connections to the host!
                     */
                    logger.info("directurl lead to 503 | too many connections");
                    return directurl;
                } else if (isDownloadableContent(con)) {
                    if (con.getCompleteContentLength() >= 0 && con.getCompleteContentLength() < 100) {
                        throw new Exception("very likely no file but an error message!length=" + con.getCompleteContentLength());
                    } else {
                        if (setFilesize && con.getCompleteContentLength() > 0) {
                            link.setVerifiedFileSize(con.getCompleteContentLength());
                        }
                        return directurl;
                    }
                } else {
                    /* Failure */
                    try {
                        br2.followConnection(true);
                    } catch (IOException e) {
                        logger.log(e);
                    }
                    throw new Exception("no downloadable content?" + con.getResponseCode() + "|" + con.getContentType() + "|" + con.isContentDisposition());
                }
            } catch (final Exception e) {
                /* Failure */
                logger.log(e);
                return null;
            } finally {
                if (con != null) {
                    try {
                        con.disconnect();
                    } catch (final Throwable e) {
                    }
                }
            }
        }
    }

    @Override
    public boolean hasAutoCaptcha() {
        /* Assume we never got auto captcha as most services will use e.g. reCaptchaV2 nowdays. */
        return false;
    }

    @Override
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null || acc.getType() == AccountType.FREE) {
            /* Anonymous downloads & Free account downloads may have captchas */
            return true;
        } else {
            /* Premium accounts don't have captchas */
            return false;
        }
    }

    /** Traits used to cleanup html of our basic browser object and put it into correctedBR. */
    public ArrayList<String> getCleanupHTMLRegexes() {
        final ArrayList<String> regexStuff = new ArrayList<String>();
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        /* generic cleanup */
        regexStuff.add("<\\!(\\-\\-.*?\\-\\-)>");
        regexStuff.add("(display: ?none;\">.*?</div>)");
        regexStuff.add("(visibility:hidden>.*?<)");
        return regexStuff;
    }

    /** Removes HTML code which could break the plugin and puts it into correctedBR. */
    protected void correctBR() throws NumberFormatException, PluginException {
        correctedBR = br.toString();
        final ArrayList<String> regexStuff = getCleanupHTMLRegexes();
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        /* generic cleanup */
        for (String aRegex : regexStuff) {
            String results[] = new Regex(correctedBR, aRegex).getColumn(0);
            if (results != null) {
                for (String result : results) {
                    correctedBR = correctedBR.replace(result, "");
                }
            }
        }
    }

    protected final String getDllink(final DownloadLink link, final Account account) {
        return getDllink(link, account, this.br, correctedBR);
    }

    /**
     * Function to find the final downloadlink. </br>
     * This will also find video directurls of embedded videos if the player is 'currently visible'.
     */
    protected String getDllink(final DownloadLink link, final Account account, final Browser br, String src) {
        String dllink = br.getRedirectLocation();
        if (dllink == null || new Regex(dllink, this.getSupportedLinks()).matches()) {
            // dllink = new Regex(src, "(\"|')(" + String.format(dllinkRegexFile, getHostsPatternPart()) + ")\\1").getMatch(1);
            // /* Use wider and wider RegEx */
            // if (dllink == null) {
            // dllink = new Regex(src, "(" + String.format(dllinkRegexFile, getHostsPatternPart()) + ")(\"|')").getMatch(0);
            // }
            if (StringUtils.isEmpty(dllink)) {
                for (final Pattern pattern : getDownloadurlRegexes()) {
                    dllink = new Regex(src, pattern).getMatch(0);
                    if (dllink != null) {
                        break;
                    }
                }
            }
            // if (dllink == null) {
            // /* Try short version */
            // dllink = new Regex(src, "(\"|')(" + String.format(dllinkRegexFile_2, getHostsPatternPart()) + ")\\1").getMatch(1);
            // }
            // if (dllink == null) {
            // /* Try short version without hardcoded domains and wide */
            // dllink = new Regex(src, "(" + String.format(dllinkRegexFile_2, getHostsPatternPart()) + ")").getMatch(0);
            // }
            /* 2019-02-02: TODO: Maybe add attempt to find downloadlink by the first url which ends with the filename */
            if (StringUtils.isEmpty(dllink)) {
                final String cryptedScripts[] = new Regex(src, "p\\}\\((.*?)\\.split\\('\\|'\\)").getColumn(0);
                if (cryptedScripts != null && cryptedScripts.length != 0) {
                    for (String crypted : cryptedScripts) {
                        dllink = decodeDownloadLink(crypted);
                        if (dllink != null) {
                            break;
                        }
                    }
                }
            }
        }
        if (StringUtils.isEmpty(dllink)) {
            dllink = getDllinkVideohost(src);
        }
        if (dllink == null && this.isImagehoster()) {
            /* Used for imagehosts */
            dllink = getDllinkImagehost(src);
        }
        if (dllink != null && Encoding.isHtmlEntityCoded(dllink)) {
            /* 2020-02-10: E.g. files.im */
            dllink = Encoding.htmlOnlyDecode(dllink);
        }
        return dllink;
    }

    protected String getDllinkImagehost(final String src) {
        /*
         * 2019-07-24: This is basically a small workaround because if a file has a "bad filename" the filename inside our URL may just look
         * like it is a thumbnail although it is not. If we find several URLs and all are the same we may still just take one of them
         * although it could be a thumbnail.
         */
        String dllink = null;
        String lastDllink = null;
        boolean allResultsAreTheSame = true;
        final ArrayList<String> possibleDllinks = new ArrayList<String>();
        for (final Pattern regex : getImageDownloadurlRegexes()) {
            final String[] dllinksTmp = new Regex(src, regex).getColumn(0);
            for (final String url : dllinksTmp) {
                possibleDllinks.add(url);
            }
        }
        for (final String possibleDllink : possibleDllinks) {
            if (possibleDllinks.size() > 1 && lastDllink != null && !possibleDllink.equalsIgnoreCase(lastDllink)) {
                allResultsAreTheSame = false;
            }
            /* Avoid downloading thumbnails */
            /* 2019-07-24: Improve recognization of thumbnails e.g. https://img67.imagetwist.com/th/123456/[a-z0-9]{12}.jpg */
            if (possibleDllink != null && !possibleDllink.matches(".+_t\\.[A-Za-z]{3,4}$")) {
                dllink = possibleDllink;
                break;
            }
            lastDllink = possibleDllink;
        }
        if (dllink == null && possibleDllinks.size() > 1 && allResultsAreTheSame) {
            logger.info("image download-candidates were all identified as thumbnails --> Using first result anyways as it is likely that it is not a thumbnail!");
            dllink = possibleDllinks.get(0);
        }
        return dllink;
    }

    /** Tries to find stream-URL for videohosts. */
    protected String getDllinkVideohost(final String src) {
        String dllink = null;
        /* RegExes for videohosts */
        String jssource = new Regex(src, "\"?sources\"?\\s*:\\s*(\\[[^\\]]+\\])").getMatch(0);
        if (StringUtils.isEmpty(jssource)) {
            /* 2019-07-04: Wider attempt - find sources via pattern of their video-URLs. */
            jssource = new Regex(src, "[A-Za-z0-9]+\\s*:\\s*(\\[[^\\]]+[a-z0-9]{60}/v\\.mp4[^\\]]+\\])").getMatch(0);
        }
        if (!StringUtils.isEmpty(jssource)) {
            logger.info("Found video json source");
            /*
             * Different services store the values we want under different names. E.g. vidoza.net uses 'res', most providers use 'label'.
             */
            final String[] possibleQualityObjectNames = new String[] { "label", "res" };
            /*
             * Different services store the values we want under different names. E.g. vidoza.net uses 'src', most providers use 'file'.
             */
            final String[] possibleStreamURLObjectNames = new String[] { "file", "src" };
            try {
                Object quality_temp_o = null;
                long quality_temp = 0;
                /*
                 * Important: Default is -1 so that even if only one quality is available without quality-identifier, it will be used!
                 */
                long quality_picked = -1;
                String dllink_temp = null;
                final List<Object> ressourcelist = (ArrayList) JavaScriptEngineFactory.jsonToJavaObject(jssource);
                final boolean onlyOneQualityAvailable = ressourcelist.size() == 1;
                final int userSelectedQuality = getPreferredStreamQuality();
                if (userSelectedQuality == -1) {
                    logger.info("Looking for BEST video stream");
                } else {
                    logger.info("Looking for user selected video stream quality: " + userSelectedQuality);
                }
                boolean foundUserSelectedQuality = false;
                for (final Object videoo : ressourcelist) {
                    /* Check for single URL without any quality information e.g. uqload.com */
                    if (videoo instanceof String && onlyOneQualityAvailable) {
                        logger.info("Only one quality available --> Returning that");
                        dllink_temp = (String) videoo;
                        if (dllink_temp.startsWith("http")) {
                            dllink = dllink_temp;
                            break;
                        }
                    }
                    final Map<String, Object> entries;
                    if (videoo instanceof Map) {
                        entries = (HashMap<String, Object>) videoo;
                        for (final String possibleStreamURLObjectName : possibleStreamURLObjectNames) {
                            if (entries.containsKey(possibleStreamURLObjectName)) {
                                dllink_temp = (String) entries.get(possibleStreamURLObjectName);
                                break;
                            }
                        }
                    } else {
                        entries = null;
                    }
                    if (StringUtils.isEmpty(dllink_temp)) {
                        /* No downloadurl found --> Continue */
                        continue;
                    } else if (dllink_temp.contains(".mpd")) {
                        /* 2020-05-20: This plugin cannot yet handle DASH stream downloads */
                        logger.info("Skipping DASH stream: " + dllink_temp);
                        continue;
                    }
                    /* Find quality + downloadurl */
                    for (final String possibleQualityObjectName : possibleQualityObjectNames) {
                        try {
                            quality_temp_o = entries.get(possibleQualityObjectName);
                            if (quality_temp_o != null && quality_temp_o instanceof Long) {
                                quality_temp = (int) JavaScriptEngineFactory.toLong(quality_temp_o, 0);
                            } else if (quality_temp_o != null && quality_temp_o instanceof String) {
                                /* E.g. '360p' */
                                quality_temp = (int) Long.parseLong(new Regex((String) quality_temp_o, "(\\d+)p?$").getMatch(0));
                            }
                            if (quality_temp > 0) {
                                break;
                            }
                        } catch (final Throwable e) {
                            /* This should never happen */
                            logger.log(e);
                            logger.info("Failed to find quality via key '" + possibleQualityObjectName + "' for current downloadurl candidate: " + dllink_temp);
                            if (!onlyOneQualityAvailable) {
                                continue;
                            }
                        }
                    }
                    if (StringUtils.isEmpty(dllink_temp)) {
                        continue;
                    }
                    if (quality_temp == userSelectedQuality) {
                        /* Found user selected quality */
                        logger.info("Found user selected quality: " + userSelectedQuality);
                        foundUserSelectedQuality = true;
                        quality_picked = quality_temp;
                        dllink = dllink_temp;
                        break;
                    } else {
                        /* Look for best quality */
                        if (quality_temp > quality_picked) {
                            quality_picked = quality_temp;
                            dllink = dllink_temp;
                        }
                    }
                }
                if (!StringUtils.isEmpty(dllink)) {
                    logger.info("Quality handling for multiple video stream sources succeeded - picked quality is: " + quality_picked);
                    if (foundUserSelectedQuality) {
                        logger.info("Successfully found user selected quality: " + userSelectedQuality);
                    } else {
                        logger.info("Successfully found BEST quality: " + quality_picked);
                    }
                } else {
                    logger.info("Failed to find any stream downloadurl");
                }
            } catch (final Throwable e) {
                logger.log(e);
                logger.info("BEST handling for multiple video source failed");
            }
        }
        if (StringUtils.isEmpty(dllink)) {
            /* 2019-07-04: Examplehost: vidoza.net */
            /* TODO: Check if we can remove regexVideoStreamDownloadURL or integrate it in this function. */
            dllink = regexVideoStreamDownloadURL(src);
        }
        if (StringUtils.isEmpty(dllink)) {
            final String check = new Regex(src, "file\\s*:\\s*\"(https?[^<>\"]*?\\.(?:mp4|flv))\"").getMatch(0);
            if (StringUtils.isNotEmpty(check) && !StringUtils.containsIgnoreCase(check, "/images/")) {
                // jwplayer("flvplayer").onError(function()...
                dllink = check;
            }
        }
        return dllink;
    }

    /** Generic RegEx to find common XFS stream download URLs */
    private final String regexVideoStreamDownloadURL(final String src) {
        String dllink = new Regex(src, Pattern.compile("(https?://[^/]+[^\"]+[a-z0-9]{60}/v\\.mp4)", Pattern.CASE_INSENSITIVE)).getMatch(0);
        if (StringUtils.isEmpty(dllink)) {
            /* Wider attempt */
            dllink = new Regex(src, Pattern.compile("\"(https?://[^/]+/[a-z0-9]{60}/[^\"]+)\"", Pattern.CASE_INSENSITIVE)).getMatch(0);
        }
        return dllink;
    }

    /** Returns user selected stream quality. -1 = BEST/no selection */
    private final int getPreferredStreamQuality() {
        final Class<? extends XFSConfigVideo> cfgO = this.getConfigInterface();
        if (cfgO != null) {
            final XFSConfigVideo cfg = PluginJsonConfig.get(cfgO);
            final PreferredStreamQuality quality = cfg.getPreferredStreamQuality();
            switch (quality) {
            default:
                return -1;
            case BEST:
                return -1;
            case Q2160P:
                return 2160;
            case Q1080P:
                return 1080;
            case Q720P:
                return 720;
            case Q480P:
                return 480;
            case Q360P:
                return 360;
            }
        } else {
            return -1;
        }
    }

    /**
     * Returns URL to the video thumbnail. <br />
     * This might sometimes be useful when VIDEOHOSTER or VIDEOHOSTER_2 handling is used.
     */
    @Deprecated
    private String getVideoThumbnailURL(final String src) {
        String url_thumbnail = new Regex(src, "image\\s*:\\s*\"(https?://[^<>\"]+)\"").getMatch(0);
        if (StringUtils.isEmpty(url_thumbnail)) {
            /* 2019-05-16: e.g. uqload.com */
            url_thumbnail = new Regex(src, "poster\\s*:\\s*\"(https?://[^<>\"]+)\"").getMatch(0);
        }
        return url_thumbnail;
    }

    public String decodeDownloadLink(final String s) {
        String decoded = null;
        try {
            Regex params = new Regex(s, "'(.*?[^\\\\])',(\\d+),(\\d+),'(.*?)'");
            String p = params.getMatch(0).replaceAll("\\\\", "");
            int a = Integer.parseInt(params.getMatch(1));
            int c = Integer.parseInt(params.getMatch(2));
            String[] k = params.getMatch(3).split("\\|");
            while (c != 0) {
                c--;
                if (k[c].length() != 0) {
                    p = p.replaceAll("\\b" + Integer.toString(c, a) + "\\b", k[c]);
                }
            }
            decoded = p;
        } catch (Exception e) {
            logger.log(e);
        }
        String dllink = null;
        if (decoded != null) {
            dllink = getDllinkVideohost(decoded);
            if (StringUtils.isEmpty(dllink)) {
                /* Open regex is possible because in the unpacked JS there are usually only 1-2 URLs. */
                dllink = new Regex(decoded, "(?:\"|')(https?://[^<>\"']*?\\.(avi|flv|mkv|mp4|m3u8))(?:\"|')").getMatch(0);
            }
            if (StringUtils.isEmpty(dllink)) {
                /* Maybe rtmp */
                dllink = new Regex(decoded, "(?:\"|')(rtmp://[^<>\"']*?mp4:[^<>\"']+)(?:\"|')").getMatch(0);
            }
        }
        return dllink;
    }

    protected boolean isDllinkFile(final String url) {
        if (!StringUtils.isEmpty(url)) {
            for (final Pattern pattern : this.getDownloadurlRegexes()) {
                final String urlMatch = new Regex(url, pattern).getMatch(0);
                if (urlMatch != null) {
                    return true;
                }
            }
        }
        return false;
    }

    protected final String getDllinkHostPattern() {
        return "[A-Za-z0-9\\-\\.]*";
    }

    /** Returns pre-download-waittime (seconds) from inside HTML. */
    protected String regexWaittime() {
        /**
         * TODO: 2019-05-15: Try to grab the whole line which contains "id"="countdown" and then grab the waittime from inside that as it
         * would probably make this more reliable.
         */
        String waitStr = new Regex(correctedBR, "id=(?:\"|\\')countdown_str(?:\"|\\')[^>]*>[^<>]*<span id=[^>]*>\\s*(\\d+)\\s*</span>").getMatch(0);
        if (waitStr == null) {
            waitStr = new Regex(correctedBR, "class=\"seconds\"[^>]*?>\\s*(\\d+)\\s*</span>").getMatch(0);
        }
        if (waitStr == null) {
            /* More open RegEx */
            waitStr = new Regex(correctedBR, "class=\"seconds\"[^>]*?>\\s*(\\d+)\\s*<").getMatch(0);
        }
        return waitStr;
    }

    /** Returns list of possible final downloadurl patterns. Match 0 will be used to find downloadurls in html source! */
    protected List<Pattern> getDownloadurlRegexes() {
        final List<Pattern> patterns = new ArrayList<Pattern>();
        /* 2020-04-01: TODO: Maybe add this part to the end: (\\s+|\\s*>|\\s*\\)|\\s*;) (?) */
        /* Allow ' in URL */
        patterns.add(Pattern.compile("\"" + String.format("(https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|%s)(?::\\d+)?/(?:files|d|cgi\\-bin/dl\\.cgi|dl)/(?:\\d+/)?[a-z0-9]+/[^<>\"/]*)", this.getDllinkHostPattern()) + "\""));
        /* Don't allow ' in URL */
        patterns.add(Pattern.compile(String.format("(https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|%s)(?::\\d+)?/(?:files|d|cgi\\-bin/dl\\.cgi|dl)/(?:\\d+/)?[a-z0-9]+/[^<>\"\\'/]*)", this.getDllinkHostPattern())));
        return patterns;
    }

    /** Returns list of possible final image-host-downloadurl patterns. Match 0 will be used to find downloadurls in html source! */
    protected List<Pattern> getImageDownloadurlRegexes() {
        final List<Pattern> patterns = new ArrayList<Pattern>();
        patterns.add(Pattern.compile(String.format("(https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|%s)(?::\\d+)?(?:/img/\\d+/[^<>\"'\\[\\]]+|/img/[a-z0-9]+/[^<>\"'\\[\\]]+|/img/[^<>\"'\\[\\]]+|/i/\\d+/[^<>\"'\\[\\]]+|/i/\\d+/[^<>\"'\\[\\]]+(?!_t\\.[A-Za-z]{3,4})))", this.getDllinkHostPattern())));
        return patterns;
    }

    /**
     * Prevents more than one free download from starting at a given time. One step prior to dl.startDownload(), it adds a slot to maxFree
     * which allows the next singleton download to start, or at least try.
     *
     * This is needed because xfileshare(website) only throws errors after a final dllink starts transferring or at a given step within pre
     * download sequence. But this template(XfileSharingProBasic) allows multiple slots(when available) to commence the download sequence,
     * this.setstartintival does not resolve this issue. Which results in x(20) captcha events all at once and only allows one download to
     * start. This prevents wasting peoples time and effort on captcha solving and|or wasting captcha trading credits. Users will experience
     * minimal harm to downloading as slots are freed up soon as current download begins.
     *
     * @param num
     *            : (+1|-1)
     */
    protected void controlMaxFreeDownloads(final Account account, final DownloadLink link, final int num) {
        if (account == null) {
            synchronized (freeRunning) {
                final int before = freeRunning.get();
                final int after = before + num;
                freeRunning.set(after);
                logger.info("freeRunning(" + link.getName() + ")|max:" + getMaxSimultanFreeDownloadNum() + "|before:" + before + "|after:" + after + "|num:" + num);
            }
        }
    }

    @Override
    protected void getPage(String page) throws Exception {
        getPage(br, page, true);
    }

    protected void getPage(final Browser br, String page, final boolean correctBr) throws Exception {
        getPage(br, page);
        if (correctBr) {
            correctBR();
        }
    }

    @Override
    protected void postPage(String page, final String postdata) throws Exception {
        postPage(br, page, postdata, true);
    }

    protected void postPage(final Browser br, String page, final String postdata, final boolean correctBr) throws Exception {
        postPage(br, page, postdata);
        if (correctBr) {
            correctBR();
        }
    }

    @Override
    protected void submitForm(final Form form) throws Exception {
        submitForm(br, form, true);
    }

    protected void submitForm(final Browser br, final Form form, final boolean correctBr) throws Exception {
        submitForm(br, form);
        if (correctBr) {
            correctBR();
        }
    }

    /**
     * Handles pre download (pre-captcha) waittime.
     */
    protected void waitTime(final DownloadLink downloadLink, final long timeBefore) throws PluginException {
        /* Ticket Time */
        final String waitStr = regexWaittime();
        if (this.preDownloadWaittimeSkippable()) {
            /* Very rare case! */
            logger.info("Skipping pre-download waittime: " + waitStr);
        } else {
            final int extraWaitSeconds = 1;
            int wait;
            if (waitStr != null && waitStr.matches("\\d+")) {
                int passedTime = (int) ((System.currentTimeMillis() - timeBefore) / 1000) - extraWaitSeconds;
                logger.info("Found waittime, parsing waittime: " + waitStr);
                wait = Integer.parseInt(waitStr);
                /*
                 * Check how much time has passed during eventual captcha event before this function has been called and see how much time
                 * is left to wait.
                 */
                wait -= passedTime;
                if (passedTime > 0) {
                    /* This usually means that the user had to solve a captcha which cuts down the remaining time we have to wait. */
                    logger.info("Total passed time during captcha: " + passedTime);
                }
            } else {
                /* No waittime at all */
                wait = 0;
            }
            if (wait > 0) {
                logger.info("Waiting final waittime: " + wait);
                sleep(wait * 1000l, downloadLink);
            } else if (wait < -extraWaitSeconds) {
                /* User needed more time to solve the captcha so there is no waittime left :) */
                logger.info("Congratulations: Time to solve captcha was higher than waittime --> No waittime left");
            } else {
                /* No waittime at all */
                logger.info("Found no waittime");
            }
        }
    }

    /**
     * This fixes filenames from all xfs modules: file hoster, audio/video streaming (including transcoded video), or blocked link checking
     * which is based on fuid.
     *
     * @version 0.4
     * @author raztoki
     */
    protected void fixFilename(final DownloadLink downloadLink) {
        /* TODO: Maybe make use of already given methods to e.g. extract filename without extension from String. */
        /* Previous (e.h. html) filename without extension */
        String orgName = null;
        /* Server filename without extension */
        String servName = null;
        /* Server filename with extension */
        String servExt = null;
        /* Either final filename from previous download attempt or filename found in HTML. */
        String orgNameExt = downloadLink.getFinalFileName();
        /* Extension of orgNameExt */
        String orgExt = null;
        if (StringUtils.isEmpty(orgNameExt)) {
            orgNameExt = downloadLink.getName();
        }
        if (!StringUtils.isEmpty(orgNameExt) && StringUtils.contains(orgNameExt, ".")) {
            orgExt = orgNameExt.substring(orgNameExt.lastIndexOf("."));
        }
        if (!StringUtils.isEmpty(orgExt)) {
            orgName = new Regex(orgNameExt, "(.+)" + Pattern.quote(orgExt)).getMatch(0);
        } else {
            /* No extension given */
            orgName = orgNameExt;
        }
        // if (orgName.endsWith("...")) orgName = orgName.replaceFirst("\\.\\.\\.$", "");
        String servNameExt = dl.getConnection() != null && getFileNameFromHeader(dl.getConnection()) != null ? Encoding.htmlDecode(getFileNameFromHeader(dl.getConnection())) : null;
        if (!StringUtils.isEmpty(servNameExt) && StringUtils.contains(servNameExt, ".")) {
            servExt = servNameExt.substring(servNameExt.lastIndexOf("."));
            servName = new Regex(servNameExt, "(.+)" + Pattern.quote(servExt)).getMatch(0);
        } else {
            /* No extension given */
            servName = servNameExt;
        }
        final String FFN;
        if (StringUtils.equalsIgnoreCase(orgName, fuid)) {
            /* Current filename only consists of fuid --> Prefer full server filename */
            FFN = servNameExt;
            logger.info("fixFileName case 1: prefer servNameExt: orgName == fuid --> Use servNameExt");
        } else if (StringUtils.isEmpty(orgExt) && !StringUtils.isEmpty(servExt) && (StringUtils.containsIgnoreCase(servName, orgName) && !StringUtils.equalsIgnoreCase(servName, orgName))) {
            /*
             * When partial match of filename exists. eg cut off by quotation mark miss match, or orgNameExt has been abbreviated by hoster
             * --> Prefer server filename
             */
            FFN = servNameExt;
            logger.info("fixFileName case 2: prefer servNameExt: previous filename had no extension given && servName contains orgName while servName != orgName --> Use servNameExt");
        } else if (!StringUtils.isEmpty(orgExt) && !StringUtils.isEmpty(servExt) && !StringUtils.equalsIgnoreCase(orgExt, servExt)) {
            /*
             * Current filename has extension given but server filename has other extension --> Swap extensions, trust the name we have but
             * use extension from server
             */
            FFN = orgName + servExt;
            logger.info(String.format("fixFileName case 3: prefer orgName + servExt: Previous filename had no extension given && servName contains orgName while servName != orgName --> Use orgName + servExt | Old ext: %s | New ext: %s", orgExt, servExt));
        } else {
            FFN = orgNameExt;
            logger.info("fixFileName case 4: prefer orgNameExt");
        }
        logger.info("fixFileName: before=" + orgNameExt + "|after=" + FFN);
        downloadLink.setFinalFileName(FFN);
    }

    /**
     * Sets XFS file-ID which is usually present inside the downloadurl added by the user. Usually it is [a-z0-9]{12}. <br />
     * Best to execute AFTER having accessed the downloadurl!
     */
    protected final void setFUID(final DownloadLink dl) throws PluginException {
        fuid = getFUIDFromURL(dl);
        /*
         * Rare case: Hoster has exotic URLs (e.g. migrated from other script e.g. YetiShare to XFS) --> Correct (internal) fuid is only
         * available via html
         */
        if (fuid == null) {
            /*
             * E.g. for hosts which migrate from other scripts such as YetiShare to XFS (example: hugesharing.net, up-4ever.org) and still
             * have their old URLs without XFS-fuid redirecting to the typical XFS URLs containing our fuid.
             */
            logger.info("fuid not given inside URL, trying to find it inside html");
            fuid = new Regex(correctedBR, "type=\"hidden\" name=\"id\" value=\"([a-z0-9]{12})\"").getMatch(0);
            if (fuid == null) {
                /* Last chance fallback */
                fuid = new Regex(br.getURL(), "https?://[^/]+/([a-z0-9]{12})").getMatch(0);
            }
            if (fuid == null) {
                /* fuid is crucial for us to have!! */
                logger.warning("Failed to find fuid inside html");
                /*
                 * 2019-06-12: Display such URLs as offline as this case is so rare that, if it happens, chances are very high that the file
                 * is offline anyways!
                 */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            logger.info("Found fuid inside html: " + fuid);
            correctDownloadLink(dl);
        }
    }

    /** Returns unique id from inside URL - usually with this pattern: [a-z0-9]{12} */
    public String getFUIDFromURL(final DownloadLink dl) {
        try {
            if (dl != null && dl.getPluginPatternMatcher() != null) {
                final String result = new Regex(new URL(dl.getPluginPatternMatcher()).getPath(), "/(?:embed-)?([a-z0-9]{12})").getMatch(0);
                return result;
            } else {
                return null;
            }
        } catch (MalformedURLException e) {
            logger.log(e);
        }
        return null;
    }

    /**
     * In some cases, URL may contain filename which can be used as fallback e.g. 'https://host.tld/<fuid>/<filename>(\\.html)?'. </br>
     * Examples without '.html' ending: vipfile.cc, prefiles.com
     */
    public String getFilenameFromURL(final DownloadLink dl) {
        try {
            String result = null;
            final String url_name_RegEx = "/[a-z0-9]{12}/(.*?)(?:\\.html)?$";
            /**
             * It's important that we check the contentURL too as we do alter pluginPatternMatcher in { @link
             * #correctDownloadLink(DownloadLink) }
             */
            if (dl.getContentUrl() != null) {
                result = new Regex(new URL(dl.getContentUrl()).getPath(), url_name_RegEx).getMatch(0);
            }
            if (result == null) {
                result = new Regex(new URL(dl.getPluginPatternMatcher()).getPath(), url_name_RegEx).getMatch(0);
            }
            return result;
        } catch (MalformedURLException e) {
            logger.log(e);
        }
        return null;
    }

    /**
     * Tries to get filename from URL and if this fails, will return <fuid> filename. <br/>
     * Execute setFUID() BEFORE YOU EXECUTE THIS OR THE PLUGIN MAY FAIL TO FIND A (fallback-) FILENAME! In very rare cases (e.g. XFS owner
     * migrated to XFS from other script) this is important! See description of setFUID for more information!
     */
    public String getFallbackFilename(final DownloadLink dl) {
        String fallback_filename = this.getFilenameFromURL(dl);
        if (fallback_filename == null) {
            fallback_filename = this.getFUIDFromURL(dl);
        }
        return fallback_filename;
    }

    protected void handlePassword(final Form pwform, final DownloadLink link) throws PluginException {
        if (isPasswordProtectedHTML(pwform)) {
            if (pwform == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            logger.info("URL is password protected");
            link.setProperty(PROPERTY_pw_required, true);
            String passCode = link.getDownloadPassword();
            if (passCode == null) {
                passCode = Plugin.getUserInput("Password?", link);
                if (StringUtils.isEmpty(passCode)) {
                    logger.info("User has entered blank password, exiting handlePassword");
                    link.setDownloadPassword(null);
                    throw new PluginException(LinkStatus.ERROR_FATAL, "Pre-Download Password not provided");
                }
            }
            logger.info("Put password \"" + passCode + "\" entered by user in the DLForm.");
            pwform.put("password", Encoding.urlEncode(passCode));
            link.setDownloadPassword(passCode);
        } else {
            link.setProperty(PROPERTY_pw_required, false);
        }
    }

    /**
     * Checks for (-& handles) all kinds of errors e.g. wrong captcha, wrong downloadpassword, waittimes and server error-responsecodes such
     * as 403, 404 and 503. <br />
     * checkAll: If enabled, ,this will also check for wrong password, wrong captcha and 'Skipped countdown' errors. <br/>
     */
    protected void checkErrors(final DownloadLink link, final Account account, final boolean checkAll) throws NumberFormatException, PluginException {
        if (checkAll) {
            if (new Regex(correctedBR, ">\\s*Wrong password").matches()) {
                final boolean websiteDidAskForPassword = link.getBooleanProperty(PROPERTY_pw_required, false);
                if (!websiteDidAskForPassword) {
                    /*
                     * 2020-03-26: Extremely rare case: Either plugin failure or serverside failure e.g. URL is password protected but
                     * website does never ask for the password e.g. 2020-03-26: ddl.to. We cannot use link.getDownloadPassword() to check
                     * this because users can enter download passwords at any time no matter whether they're required/used or not.
                     */
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server says 'wrong password' but never prompted for one");
                } else {
                    final String userEnteredPassword = link.getDownloadPassword();
                    /* handle password has failed in the past, additional try catching / resetting values */
                    logger.warning("Wrong password, the entered password \"" + userEnteredPassword + "\" is wrong, retrying...");
                    link.setDownloadPassword(null);
                    throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
                }
            }
            if (correctedBR.contains("Wrong captcha")) {
                logger.warning("Wrong captcha (or wrong password as well)!");
                /*
                 * TODO: Find a way to avoid using a property for this or add the property in very plugin which overrides handleCaptcha e.g.
                 * subyshare.com. If a dev forgets to set this, it will cause invalid errormessages on wrong captcha!
                 */
                final boolean websiteDidAskForCaptcha = link.getBooleanProperty(PROPERTY_captcha_required, false);
                if (websiteDidAskForCaptcha) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                } else {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server says 'wrong captcha' but never prompted for one");
                }
            }
            if (new Regex(correctedBR, ">\\s*Skipped countdown\\s*<").matches()) {
                /* 2019-08-28: e.g. "<br><b class="err">Skipped countdown</b><br>" */
                throw new PluginException(LinkStatus.ERROR_FATAL, "Fatal countdown error (countdown skipped)");
            }
        }
        /** Wait time reconnect handling */
        final String limitBasedOnNumberofFilesAndTime = new Regex(correctedBR, ">(You have reached the maximum limit \\d+ files in \\d+ hours)").getMatch(0);
        final String preciseWaittime = new Regex(correctedBR, "((You have reached the download(\\-| )limit|You have to wait)[^<>]+)").getMatch(0);
        if (preciseWaittime != null) {
            /* Reconnect waittime with given (exact) waittime usually either up to the minute or up to the second. */
            final String tmphrs = new Regex(preciseWaittime, "\\s*(\\d+)\\s*hours?").getMatch(0);
            final String tmpmin = new Regex(preciseWaittime, "\\s*(\\d+)\\s*minutes?").getMatch(0);
            final String tmpsec = new Regex(preciseWaittime, "\\s*(\\d+)\\s*seconds?").getMatch(0);
            final String tmpdays = new Regex(preciseWaittime, "\\s*(\\d+)\\s*days?").getMatch(0);
            int waittime;
            if (tmphrs == null && tmpmin == null && tmpsec == null && tmpdays == null) {
                /* This should not happen! This is an indicator of developer-failure! */
                logger.info("Waittime RegExes seem to be broken - using default waittime");
                waittime = 60 * 60 * 1000;
            } else {
                int minutes = 0, seconds = 0, hours = 0, days = 0;
                if (tmphrs != null) {
                    hours = Integer.parseInt(tmphrs);
                }
                if (tmpmin != null) {
                    minutes = Integer.parseInt(tmpmin);
                }
                if (tmpsec != null) {
                    seconds = Integer.parseInt(tmpsec);
                }
                if (tmpdays != null) {
                    days = Integer.parseInt(tmpdays);
                }
                waittime = ((days * 24 * 3600) + (3600 * hours) + (60 * minutes) + seconds + 1) * 1000;
            }
            logger.info("Detected reconnect waittime (milliseconds): " + waittime);
            /* Not enough wait time to reconnect -> Wait short and retry */
            if (waittime < 180000) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait until new downloads can be started", waittime);
            } else if (account != null) {
                /*
                 * 2020-04-17: Some hosts will have trafficlimit and e.g. only allow one file every X minutes so his errormessage might be
                 * confusing to some users. Now it should cover both cases at the same time.
                 */
                throw new AccountUnavailableException("Download limit reached or wait until next download can be started", waittime);
            } else {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, waittime);
            }
        } else if (limitBasedOnNumberofFilesAndTime != null) {
            /*
             * 2019-05-09: New: Seems like XFS owners can even limit by number of files inside specified timeframe. Example: hotlink.cc; 150
             * files per 24 hours
             */
            /* Typically '>You have reached the maximum limit 150 files in 24 hours' */
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, limitBasedOnNumberofFilesAndTime);
        } else if (correctedBR.contains("You're using all download slots for IP")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Server error 'You're using all download slots for IP'", 10 * 60 * 1001l);
        } else if (correctedBR.contains("Error happened when generating Download Link")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Error happened when generating Download Link'", 10 * 60 * 1000l);
        }
        /** Error handling for premiumonly links */
        if (isPremiumOnly()) {
            String filesizelimit = new Regex(correctedBR, "You can download files up to(.*?)only").getMatch(0);
            if (filesizelimit != null) {
                filesizelimit = filesizelimit.trim();
                throw new AccountRequiredException("As free user you can download files up to " + filesizelimit + " only");
            } else {
                logger.info("Only downloadable via premium");
                throw new AccountRequiredException();
            }
        } else if (isPremiumOnly()) {
            logger.info("Only downloadable via premium");
            throw new AccountRequiredException();
        } else if (new Regex(correctedBR, ">\\s*Expired download session").matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Expired download session'", 10 * 60 * 1000l);
        }
        if (isWebsiteUnderMaintenance()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is under maintenance", 2 * 60 * 60 * 1000l);
        }
        /* Host-type specific errors */
        /* Videohoster */
        if (new Regex(correctedBR, ">\\s*Video is processing now").matches()) {
            /* E.g. '<div id="over_player_msg">Video is processing now. <br>Conversion stage: <span id='enc_pp'>...</span></div>' */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Not (yet) downloadable: Video is still being encoded or broken", 10 * 60 * 1000l);
        }
        /*
         * Errorhandling for accounts that are valid but cannot be used yet because the user has to add his mail to the account via website.
         * E.g. accounts which have been generated via balance/points of uploaders' accounts. This should be a rare case. In this case,
         * every request you do on the website will redirect to /?op=my_account along with an errormessage (sometimes).
         */
        if (account != null && (StringUtils.containsIgnoreCase(br.getURL(), "op=my_account") || StringUtils.containsIgnoreCase(br.getRedirectLocation(), "op=my_account"))) {
            /* Try to make this language-independant: Rely only on URL and NOT html! */
            // if (new Regex(correctedBR, ">\\s*?Please enter your e-mail").matches())
            final String accountErrorMsg;
            if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                accountErrorMsg = String.format("Ergänze deine E-Mail Adresse unter %s/?op=my_account um diesen Account verwenden zu können!", this.getHost());
            } else {
                accountErrorMsg = String.format("Go to %s/?op=my_account and enter your e-mail in order to be able to use this account!", this.getHost());
            }
            throw new AccountUnavailableException(accountErrorMsg, 10 * 60 * 1000l);
        }
        checkResponseCodeErrors(br.getHttpConnection());
    }

    /* Use this during download handling instead of just throwing PluginException with LinkStatus ERROR_PLUGIN_DEFECT! */
    protected void checkErrorsLastResort(final Account account) throws PluginException {
        logger.info("Last resort errorhandling");
        if (account != null && !this.isLoggedin()) {
            throw new AccountUnavailableException("Session expired?", 5 * 60 * 1000l);
        }
        String website_error = new Regex(correctedBR, "class=\"err\"[^>]*?>([^<>]+)<").getMatch(0);
        if (website_error != null) {
            if (Encoding.isHtmlEntityCoded(website_error)) {
                website_error = Encoding.htmlDecode(website_error);
            }
            logger.info("Found website error: " + website_error);
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, website_error, 5 * 60 * 1000l);
        }
        /* 2020-06-05 E.g. <div id="over_player_msg">File is awaiting for moderation</div> */
        String website_error_videoplayer = new Regex(correctedBR, "id=\"over_player_msg\"[^>]*?>([^<>\"]+)<").getMatch(0);
        if (website_error_videoplayer != null) {
            if (Encoding.isHtmlEntityCoded(website_error_videoplayer)) {
                website_error = Encoding.htmlDecode(website_error_videoplayer);
            }
            logger.info("Found website videoplayer error: " + website_error_videoplayer);
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, website_error_videoplayer, 5 * 60 * 1000l);
        }
        logger.warning("Unknown error happened");
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    /** Handles all kinds of error-responsecodes! Same for API and website! */
    public void checkResponseCodeErrors(final URLConnectionAdapter con) throws PluginException {
        if (con != null) {
            final long responsecode = con.getResponseCode();
            if (responsecode == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 5 * 60 * 1000l);
            } else if (responsecode == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 5 * 60 * 1000l);
            } else if (responsecode == 416) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 416", 5 * 60 * 1000l);
            } else if (responsecode == 500) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 500", 5 * 60 * 1000l);
            } else if (responsecode == 503) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server error 503 connection limit reached", 5 * 60 * 1000l);
            }
        }
    }

    /**
     * Handles all kinds of errors which can happen if we get the final downloadlink but we get html code instead of the file we want to
     * download.
     */
    public void checkServerErrors(final DownloadLink link, final Account account) throws NumberFormatException, PluginException {
        if (new Regex(correctedBR.trim(), "^No file$").matches()) {
            /* Possibly dead file but it is supposed to be online so let's wait and retry! */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "^Wrong IP$").matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: 'Wrong IP'", 2 * 60 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "^Expired$").matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: 'Expired'", 2 * 60 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "(^File Not Found$|<h1>404 Not Found</h1>)").matches()) {
            /* most likely result of generated link that has expired -raztoki */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        }
    }

    protected boolean supports_lifetime_account() {
        return false;
    }

    protected boolean is_lifetime_account() {
        return new Regex(correctedBR, ">Premium account expire</TD><TD><b>Lifetime</b>").matches();
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai;
        if (this.enable_account_api_only_mode()) {
            ai = this.fetchAccountInfoAPI(this.br, account);
        } else {
            ai = this.fetchAccountInfoWebsite(account);
        }
        return ai;
    }

    protected AccountInfo fetchAccountInfoWebsite(final Account account) throws Exception {
        AccountInfo ai = null;
        loginWebsite(account, true);
        final String account_info_url_relative = getRelativeAccountInfoURL();
        /*
         * Only access URL if we haven't accessed it before already. Some sites will redirect to their Account-Info page right after
         * logging-in or our login-function when it is verifying cookies and not performing a full login.
         */
        if (br.getURL() == null || !br.getURL().contains(account_info_url_relative)) {
            getPage(this.getMainPage() + account_info_url_relative);
        }
        boolean api_success = false;
        {
            /*
             * 2019-07-11: apikey handling - prefer account info via API instead of website if allowed.
             */
            String apikey = null;
            try {
                /*
                 * 2019-08-13: Do not hand over corrected_br as source as correctBR() might remove important parts of the html and because
                 * XFS owners will usually not add html traps into the html of accounts (especially ) we can use the original unmodified
                 * html here.
                 */
                apikey = this.findAPIKey(br.toString());
            } catch (InterruptedException e) {
                throw e;
            } catch (final Throwable e) {
                /*
                 * 2019-08-16: All kinds of errors may happen when trying to access the API. It is preferable if it works but we cannot rely
                 * on it working so we need that website fallback!
                 */
                logger.info("Failed to find apikey (with Exception) --> Continuing via website");
                logger.log(e);
            }
            if (apikey != null) {
                /*
                 * 2019-07-11: Use API even if 'supports_api()' is disabled because if it works it is a much quicker and more reliable way
                 * to get account information.
                 */
                logger.info("Found apikey --> Trying to get accountinfo via API");
                /* Save apikey for possible future usage */
                synchronized (account) {
                    account.setProperty(PROPERTY_ACCOUNT_apikey, apikey);
                    try {
                        ai = this.fetchAccountInfoAPI(this.br.cloneBrowser(), account);
                        api_success = ai != null;
                    } catch (final InterruptedException e) {
                        throw e;
                    } catch (final Throwable e) {
                        e.printStackTrace();
                        logger.warning("Failed to find accountinfo via API even though apikey is given; probably serverside API failure --> Fallback to website handling");
                    }
                }
            }
        }
        if (ai == null) {
            /*
             * apikey can also be used e.g. for mass-linkchecking - make sure that we keep only a valid apikey --> Remove apikey if
             * accountcheck via API fails!
             */
            account.removeProperty(PROPERTY_ACCOUNT_apikey);
            /*
             * Do not remove the saved API domain because if a user e.g. adds an apikey without adding an account later on, it might still
             * be useful!
             */
            // this.getPluginConfig().removeProperty(PROPERTY_PLUGIN_api_domain_with_protocol);
            /* Use new AccountInfo object to use with account data from website. */
            ai = new AccountInfo();
        }
        if (api_success && !ai.isUnlimitedTraffic()) {
            /* trafficleft given via API. TODO: Allow fetchAccountInfoAPI to set unlimited traffic and trust it here. */
            logger.info("Successfully found complete AccountInfo with trafficleft via API");
            return ai;
        }
        /*
         * trafficleft is usually not given via API so we'll have to check for it via website. Also we do not trsut 'unlimited traffic' via
         * API yet.
         */
        String trafficLeftStr = regExTrafficLeft();
        /* Example non english: brupload.net */
        final boolean userHasUnlimitedTraffic = trafficLeftStr != null && trafficLeftStr.matches(".*?(nlimited|Ilimitado).*?");
        if (trafficLeftStr != null && !userHasUnlimitedTraffic && !trafficLeftStr.equalsIgnoreCase("Mb")) {
            trafficLeftStr = Encoding.htmlDecode(trafficLeftStr);
            trafficLeftStr.trim();
            /* Need to set 0 traffic left, as getSize returns positive result, even when negative value supplied. */
            long trafficLeft = 0;
            if (trafficLeftStr.startsWith("-")) {
                /* Negative traffic value = User downloaded more than he is allowed to (rare case) --> No traffic left */
                trafficLeft = 0;
            } else {
                trafficLeft = SizeFormatter.getSize(trafficLeftStr);
            }
            /* 2019-02-19: Users can buy additional traffic packages: Example(s): subyshare.com */
            final String usableBandwidth = br.getRegex("Usable Bandwidth\\s*<span[^>]*>\\s*([0-9\\.]+\\s*[TGMKB]+)\\s*/\\s*[0-9\\.]+\\s*[TGMKB]+\\s*<").getMatch(0);
            if (usableBandwidth != null) {
                trafficLeft = Math.max(trafficLeft, SizeFormatter.getSize(usableBandwidth));
            }
            ai.setTrafficLeft(trafficLeft);
        } else {
            ai.setUnlimitedTraffic();
        }
        if (api_success) {
            logger.info("Successfully found AccountInfo without trafficleft via API (fetched trafficleft via website)");
            return ai;
        }
        final String space[] = new Regex(correctedBR, ">Used space:</td>.*?<td.*?b>([0-9\\.]+) ?(KB|MB|GB|TB)?</b>").getRow(0);
        if ((space != null && space.length != 0) && (space[0] != null && space[1] != null)) {
            /* free users it's provided by default */
            ai.setUsedSpace(space[0] + " " + space[1]);
        } else if ((space != null && space.length != 0) && space[0] != null) {
            /* premium users the Mb value isn't provided for some reason... */
            ai.setUsedSpace(space[0] + "Mb");
        }
        if (supports_lifetime_account() && is_lifetime_account()) {
            ai.setValidUntil(-1);
            setAccountLimitsByType(account, AccountType.LIFETIME);
        } else {
            /* 2019-07-11: It is not uncommon for XFS websites to display expire-dates even though the account is not premium anymore! */
            String expireStr = new Regex(correctedBR, "(\\d{1,2} (January|February|March|April|May|June|July|August|September|October|November|December) \\d{4})").getMatch(0);
            long expire_milliseconds = 0;
            long expire_milliseconds_from_expiredate = 0;
            long expire_milliseconds_precise_to_the_second = 0;
            if (expireStr != null) {
                /*
                 * 2019-12-17: XFS premium accounts usually don't expire just before the next day. They will end to the same time of the day
                 * when they were bought but website only displays it to the day which is why we set it to just before the next day to
                 * prevent them from expiring too early in JD. XFS websites with API may provide more precise information on the expiredate
                 * (down to the second).
                 */
                expireStr += " 23:59:59";
                expire_milliseconds_from_expiredate = TimeFormatter.getMilliSeconds(expireStr, "dd MMMM yyyy HH:mm:ss", Locale.ENGLISH);
            }
            final String[] supports_precise_expire_date = this.supports_precise_expire_date();
            if (supports_precise_expire_date != null && supports_precise_expire_date.length > 0) {
                /*
                 * A more accurate expire time, down to the second. Usually shown on 'extend premium account' page. Case[0] e.g.
                 * 'flashbit.cc', Case [1] e.g. takefile.link, example website which has no precise expiredate at all: anzfile.net
                 */
                final List<String> paymentURLs;
                final String last_working_payment_url = this.getPluginConfig().getStringProperty("property_last_working_payment_url", null);
                if (last_working_payment_url != null) {
                    paymentURLs = new ArrayList<String>();
                    logger.info("Found stored last_working_payment_url --> Trying this first in an attempt to save http requests: " + last_working_payment_url);
                    paymentURLs.add(last_working_payment_url);
                    /* Add all remaining URLs, start with the last working one */
                    for (final String paymentURL : supports_precise_expire_date) {
                        if (!paymentURLs.contains(paymentURL)) {
                            paymentURLs.add(paymentURL);
                        }
                    }
                } else {
                    /* Add all possible payment URLs. */
                    logger.info("last_working_payment_url is not available --> Going through all possible paymentURLs");
                    paymentURLs = Arrays.asList(supports_precise_expire_date);
                }
                /* Go through possible paymentURLs in an attempt to find an exact expiredate if the account is premium. */
                for (final String paymentURL : paymentURLs) {
                    if (StringUtils.isEmpty(paymentURL)) {
                        continue;
                    } else {
                        try {
                            getPage(paymentURL);
                        } catch (final Throwable e) {
                            logger.log(e);
                            /* Skip failures due to timeout or bad http error-responses */
                            continue;
                        }
                    }
                    /* Find html snippet which should contain our expiredate. */
                    final String preciseExpireHTML = new Regex(correctedBR, "<div[^>]*class=\"accexpire\"[^>]*>.*?</div>").getMatch(-1);
                    String expireSecond = new Regex(preciseExpireHTML, "Premium(-| )Account expires?\\s*:\\s*(?:</span>)?\\s*(?:<span>)?\\s*([a-zA-Z0-9, ]+)\\s*</").getMatch(-1);
                    if (StringUtils.isEmpty(expireSecond)) {
                        /*
                         * Last attempt - wider RegEx but we expect the 'second(s)' value to always be present!! Example: file-up.org:
                         * "<p style="direction: ltr; display: inline-block;">1 year, 352 days, 22 hours, 36 minutes, 45 seconds</p>"
                         */
                        expireSecond = new Regex(preciseExpireHTML, Pattern.compile(">\\s*(\\d+ years?, )?(\\d+ days?, )?(\\d+ hours?, )?(\\d+ minutes?, )?\\d+ seconds\\s*<", Pattern.CASE_INSENSITIVE)).getMatch(-1);
                    }
                    if (StringUtils.isEmpty(expireSecond) && !StringUtils.isEmpty(preciseExpireHTML)) {
                        /*
                         * 2019-09-07: This html-class may also be given for non-premium accounts e.g. fileup.cc
                         */
                        logger.info("html contains 'accexpire' class but we failed to find a precise expiredate --> Either we have a free account or failed to find precise expiredate although it is given");
                    }
                    if (!StringUtils.isEmpty(expireSecond)) {
                        String tmpYears = new Regex(expireSecond, "(\\d+)\\s+years?").getMatch(0);
                        String tmpdays = new Regex(expireSecond, "(\\d+)\\s+days?").getMatch(0);
                        String tmphrs = new Regex(expireSecond, "(\\d+)\\s+hours?").getMatch(0);
                        String tmpmin = new Regex(expireSecond, "(\\d+)\\s+minutes?").getMatch(0);
                        String tmpsec = new Regex(expireSecond, "(\\d+)\\s+seconds?").getMatch(0);
                        long years = 0, days = 0, hours = 0, minutes = 0, seconds = 0;
                        if (!StringUtils.isEmpty(tmpYears)) {
                            years = Integer.parseInt(tmpYears);
                        }
                        if (!StringUtils.isEmpty(tmpdays)) {
                            days = Integer.parseInt(tmpdays);
                        }
                        if (!StringUtils.isEmpty(tmphrs)) {
                            hours = Integer.parseInt(tmphrs);
                        }
                        if (!StringUtils.isEmpty(tmpmin)) {
                            minutes = Integer.parseInt(tmpmin);
                        }
                        if (!StringUtils.isEmpty(tmpsec)) {
                            seconds = Integer.parseInt(tmpsec);
                        }
                        expire_milliseconds_precise_to_the_second = ((years * 86400000 * 365) + (days * 86400000) + (hours * 3600000) + (minutes * 60000) + (seconds * 1000));
                    }
                    if (expire_milliseconds_precise_to_the_second > 0) {
                        /* Later we will decide whether we are going to use this value or not. */
                        logger.info("Successfully found precise expire-date via paymentURL: \"" + paymentURL + "\" : " + expireSecond);
                        this.getPluginConfig().setProperty("property_last_working_payment_url", paymentURL);
                        break;
                    } else {
                        logger.info("Failed to find precise expire-date via paymentURL: \"" + paymentURL + "\"");
                    }
                }
            }
            final long currentTime = br.getCurrentServerTime(System.currentTimeMillis());
            if (expire_milliseconds_precise_to_the_second > 0) {
                /* Add current time to parsed value */
                expire_milliseconds_precise_to_the_second += currentTime;
            }
            if (expire_milliseconds_precise_to_the_second > 0) {
                logger.info("Using precise expire-date");
                expire_milliseconds = expire_milliseconds_precise_to_the_second;
            } else if (expire_milliseconds_from_expiredate > 0) {
                logger.info("Using expire-date which is up to 24 hours precise");
                expire_milliseconds = expire_milliseconds_from_expiredate;
            } else {
                logger.info("Failed to find any useful expire-date at all");
            }
            if ((expire_milliseconds - currentTime) <= 0) {
                /* If the premium account is expired or we cannot find an expire-date we'll simply accept it as a free account. */
                if (expire_milliseconds > 0) {
                    logger.info("Premium expired --> Free account");
                } else {
                    logger.info("Account is a FREE account as no expiredate has been found");
                }
                setAccountLimitsByType(account, AccountType.FREE);
            } else {
                /* Expire date is in the future --> It is a premium account */
                logger.info("Premium account");
                ai.setValidUntil(expire_milliseconds);
                setAccountLimitsByType(account, AccountType.PREMIUM);
            }
        }
        return ai;
    }

    /**
     * Tries to find apikey on website which, if given, usually camn be found on /?op=my_account Example host which has 'API mod'
     * installed:</br>
     * This will also try to get- and save the API host with protocol in case it differs from the plugins' main host (examples:
     * ddownload.co, vup.to). clicknupload.org </br>
     * apikey will usually be located here: "/?op=my_account"
     */
    protected String findAPIKey(String src) throws Exception {
        /*
         * 2019-07-11: apikey handling - prefer that instead of website. Even if an XFS website has the "API mod" enabled, we will only find
         * a key here if the user at least once pressed the "Generate API Key" button or if the XFS 'api mod' used by the website admin is
         * configured to display apikeys by default for all users.
         */
        String apikey = regexAPIKey(src);
        String generate_apikey_url = new Regex(src, "\"([^\"]*?op=my_account[^\"]*?generate_api_key=1[^\"]*?token=[a-f0-9]{32}[^\"]*?)\"").getMatch(0);
        /*
         * 2019-07-28: If no apikey has ever been generated by the user but generate_apikey_url != null we can generate the first apikey
         * automatically.
         */
        if (StringUtils.isEmpty(apikey) && generate_apikey_url != null) {
            if (Encoding.isHtmlEntityCoded(generate_apikey_url)) {
                /*
                 * 2019-07-28: Some hosts have "&&amp;" inside URL (= buggy) - also some XFS hosts will only allow apikey generation once
                 * and when pressing "change key" afterwards, it will always be the same. This may also be a serverside XFS bug.
                 */
                generate_apikey_url = Encoding.htmlDecode(generate_apikey_url);
            }
            logger.info("Failed to find apikey but host has api-mod enabled --> Trying to generate first apikey for this account");
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                getPage(br2, generate_apikey_url);
                src = br2.toString();
                apikey = regexAPIKey(src);
            } catch (final Throwable e) {
                e.printStackTrace();
            }
            if (apikey == null) {
                /*
                 * 2019-10-01: Some hosts will not display an APIKey immediately e.g. vup.to 'New API key generated. Please wait 1-2 minutes
                 * while the key is being generated and refresh the page afterwards.'. This should not be an issue for us as the APIKey will
                 * be detected upon next account-check.
                 */
                logger.info("Failed to find generated apikey - possible plugin failure");
            } else {
                logger.info("Successfully found newly generated apikey: " + apikey);
            }
        }
        if (apikey != null) {
            logger.info("Trying to find api domain with protocol");
            String url_with_apikey = new Regex(src, "(https?://[^/]+/api/account/info[^<>\"\\']*key=" + apikey + "[^<>\"\\']*)").getMatch(0);
            boolean api_uses_special_domain = false;
            if (url_with_apikey == null) {
                logger.info("Unable to find API domain - assuming it is the same es the plugins'");
            } else {
                try {
                    if (Encoding.isHtmlEntityCoded(url_with_apikey)) {
                        /*
                         * 2019-07-28: Some hosts have "&&amp;" inside URL (= buggy) - also some XFS hosts will only allow apikey generation
                         * once and when pressing "change key" afterwards, it will always be the same. This may also be a serverside XFS
                         * bug.
                         */
                        url_with_apikey = Encoding.htmlDecode(url_with_apikey);
                    }
                    final URL apiurl = new URL(url_with_apikey);
                    final String apihost = Browser.getHost(apiurl, true);
                    if (!apihost.equalsIgnoreCase(this.getHost())) {
                        logger.info(String.format("API domain is %s while main domain of plugin is %s", apihost, this.getHost()));
                        api_uses_special_domain = true;
                        final String test = apiurl.getProtocol() + "://" + apiurl.getHost() + "/api";
                        this.getPluginConfig().setProperty(PROPERTY_PLUGIN_api_domain_with_protocol, test);
                    } else {
                        logger.info("API domain and main domain are the same: " + this.getHost());
                    }
                } catch (final Throwable e) {
                    logger.info("Error while trying to find API domain");
                }
            }
            if (!api_uses_special_domain) {
                /* Important: Dump old data - maybe apihost was different and is now the same! */
                this.getPluginConfig().removeProperty(PROPERTY_PLUGIN_api_domain_with_protocol);
            }
        }
        return apikey;
    }

    /** Override this if default handling does not find the APIKey */
    protected String regexAPIKey(final String src) {
        final Pattern apikeyPattern = Pattern.compile("/api/account/info\\?key=([a-z0-9]+)");
        return new Regex(src, apikeyPattern).getMatch(0);
    }

    protected void setAccountLimitsByType(final Account account, final AccountType type) {
        account.setType(type);
        switch (type) {
        case LIFETIME:
        case PREMIUM:
            account.setConcurrentUsePossible(true);
            account.setMaxSimultanDownloads(this.getMaxSimultanPremiumDownloadNum());
            break;
        case FREE:
            account.setConcurrentUsePossible(false);
            account.setMaxSimultanDownloads(getMaxSimultaneousFreeAccountDownloads());
            break;
        case UNKNOWN:
        default:
            account.setConcurrentUsePossible(false);
            account.setMaxSimultanDownloads(1);
            break;
        }
    }

    public Form findLoginform(final Browser br) {
        Form loginform = br.getFormbyProperty("name", "FL");
        if (loginform == null) {
            /* More complicated way to find loginform ... */
            final Form[] allForms = this.br.getForms();
            for (final Form aForm : allForms) {
                final InputField inputFieldOP = aForm.getInputFieldByName("op");
                if (inputFieldOP != null && "login".equalsIgnoreCase(inputFieldOP.getValue())) {
                    loginform = aForm;
                    break;
                }
            }
        }
        return loginform;
    }

    /** Returns Form required to click on 'continue to image' for image-hosts. */
    public Form findImageForm(final Browser br) {
        final Form imghost_next_form = br.getFormbyKey("next");
        if (imghost_next_form != null && imghost_next_form.hasInputFieldByName("method_premium")) {
            imghost_next_form.remove("method_premium");
        }
        return imghost_next_form;
    }

    /** Tries to find available traffic-left value inside html code. */
    protected String regExTrafficLeft() {
        /* 2020-30-09: progressbar with tooltip */
        String availabletraffic = new Regex(this.correctedBR, "Traffic available(?:\\s*today)?\\s*[^<>]*:?(?:<[^>]*>)?</TD>\\s*<TD[^>]*>\\s*<div[^>]*title\\s*=\\s*\"\\s*([^<>\"']+)\\s*available").getMatch(0);
        if (StringUtils.isEmpty(availabletraffic)) {
            /* Traffic can also be negative! */
            availabletraffic = new Regex(this.correctedBR, "Traffic available(?:\\s*today)?\\s*[^<>]*:?(?:<[^>]*>)?</TD>\\s*<TD[^>]*>\\s*(?:<b[^>]*>)?\\s*([^<>\"']+)").getMatch(0);
            if (StringUtils.isEmpty(availabletraffic)) {
                /* 2019-02-11: For newer XFS versions */
                availabletraffic = new Regex(this.correctedBR, ">\\s*Traffic available(?:\\s*today)?\\s*</div>\\s*<div class=\"txt\\d+\">\\s*([^<>\"]+)\\s*<").getMatch(0);
            }
        }
        if (StringUtils.isNotEmpty(availabletraffic)) {
            return availabletraffic;
        } else {
            return null;
        }
    }

    /**
     * Verifies logged-in state via multiple factors.
     *
     * @return true: Implies that user is logged-in. <br />
     *         false: Implies that user is not logged-in. A full login with login-credentials is required! <br />
     */
    public boolean isLoggedin() {
        /**
         * please use valid combinations only! login or email alone without xfss is NOT valid!
         */
        /**
         * 2019-07-25: TODO: Maybe check for valid cookies on all supported domains (e.g. special case imgrock.info and some others in
         * ImgmazeCom plugin)
         */
        final String mainpage = getMainPage();
        logger.info("Doing login-cookiecheck for: " + mainpage);
        final boolean login_xfss_CookieOkay = StringUtils.isAllNotEmpty(br.getCookie(mainpage, "login", Cookies.NOTDELETEDPATTERN), br.getCookie(mainpage, "xfss", Cookies.NOTDELETEDPATTERN));
        /* xfsts cookie is mostly used in xvideosharing sites (videohosters) example: vidoza.net */
        final boolean login_xfsts_CookieOkay = StringUtils.isAllNotEmpty(br.getCookie(mainpage, "login", Cookies.NOTDELETEDPATTERN), br.getCookie(mainpage, "xfsts", Cookies.NOTDELETEDPATTERN));
        /* 2019-06-21: Example website which uses rare email cookie: filefox.cc (so far the only known!) */
        final boolean email_xfss_CookieOkay = StringUtils.isAllNotEmpty(br.getCookie(mainpage, "email", Cookies.NOTDELETEDPATTERN), br.getCookie(mainpage, "xfss", Cookies.NOTDELETEDPATTERN));
        final boolean email_xfsts_CookieOkay = StringUtils.isAllNotEmpty(br.getCookie(mainpage, "email", Cookies.NOTDELETEDPATTERN), br.getCookie(mainpage, "xfsts", Cookies.NOTDELETEDPATTERN));
        /* buttons or sites that are only available for logged in users */
        // remove script tags
        // remove comments, eg ddl.to just comment some buttons/links for expired cookies/non logged in
        final String htmlWithoutScriptTagsAndComments = br.toString().replaceAll("(?s)(<script.*?</script>)", "").replaceAll("(?s)(<!--.*?-->)", "");
        final String ahref = "<a[^<]*href\\s*=\\s*\"[^\"]*";
        final boolean logoutOkay = new Regex(htmlWithoutScriptTagsAndComments, ahref + "(&|\\?)op=logout").matches() || new Regex(htmlWithoutScriptTagsAndComments, ahref + "/(user_)?logout\"").matches();
        // unsafe, not every site does redirect
        final boolean loginURLFailed = br.getURL().contains("op=") && br.getURL().contains("op=login");
        /*
         * 2019-11-11: Set myAccountOkay to true if there is currently a redirect which means in this situation we rely on our cookie ONLY.
         * This may be the case if a user has direct downloads enabled. We access downloadurl --> Redirect happens --> We check for login
         */
        final boolean isRedirect = br.getRedirectLocation() != null;
        final boolean myAccountOkay = (new Regex(htmlWithoutScriptTagsAndComments, ahref + "(&|\\?)op=my_account").matches() || new Regex(htmlWithoutScriptTagsAndComments, ahref + "/my(-|_)account\"").matches() || isRedirect);
        logger.info("login_xfss_CookieOkay:" + login_xfss_CookieOkay);
        logger.info("login_xfsts_CookieOkay:" + login_xfsts_CookieOkay);
        logger.info("email_xfss_CookieOkay:" + email_xfss_CookieOkay);
        logger.info("email_xfsts_CookieOkay:" + email_xfsts_CookieOkay);
        logger.info("logoutOkay:" + logoutOkay);
        logger.info("myAccountOkay:" + myAccountOkay);
        logger.info("loginURLFailed:" + loginURLFailed);
        final boolean ret = (login_xfss_CookieOkay || email_xfss_CookieOkay || login_xfsts_CookieOkay || email_xfsts_CookieOkay) && ((logoutOkay || myAccountOkay) && !loginURLFailed);
        logger.info("loggedin:" + ret);
        return ret;
    }

    /** Returns the full URL to the page which should contain the loginForm. */
    public String getLoginURL() {
        return getMainPage() + "/login.html";
    }

    /**
     * Returns the relative URL to the page which should contain all account information (account type, expiredate, apikey, remaining
     * traffic).
     */
    protected String getRelativeAccountInfoURL() {
        return "/?op=my_account";
    }

    /**
     * @param validateCookies
     *            true = Check whether stored cookies are still valid, if not, perform full login <br/>
     *            false = Set stored cookies and trust them if they're not older than 300000l
     *
     */
    public boolean loginWebsite(final Account account, final boolean validateCookies) throws Exception {
        synchronized (account) {
            final boolean followRedirects = br.isFollowingRedirects();
            try {
                /* Load cookies */
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    logger.info("Stored login-Cookies are available");
                    br.setCookies(getMainPage(), cookies);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= 300000l && !validateCookies) {
                        /* We trust these cookies as they're not that old --> Do not check them */
                        logger.info("Trust login-cookies without checking as they should still be fresh");
                        return false;
                    }
                    logger.info("Verifying login-cookies");
                    getPage(getMainPage() + getRelativeAccountInfoURL());
                    if (isLoggedin()) {
                        logger.info("Successfully logged in via cookies");
                        account.saveCookies(br.getCookies(getMainPage()), "");
                        return true;
                    } else {
                        logger.info("Cookie login failed");
                    }
                }
                /*
                 * 2019-08-20: Some hosts (rare case) will fail on the first attempt even with correct logindata and then demand a captcha.
                 * Example: filejoker.net
                 */
                logger.info("Full login required");
                final Cookies userCookies = Cookies.parseCookiesFromJsonString(account.getPass());
                if (userCookies != null) {
                    /* Fallback */
                    logger.info("Verifying user-login-cookies");
                    br.clearCookies(this.getHost());
                    br.setCookies(getMainPage(), userCookies);
                    getPage(getMainPage() + getRelativeAccountInfoURL());
                    if (isLoggedin()) {
                        logger.info("Successfully logged in via cookies");
                        account.saveCookies(br.getCookies(getMainPage()), "");
                        String cookiesUsername = br.getCookie(br.getHost(), "login", Cookies.NOTDELETEDPATTERN);
                        if (cookiesUsername == null) {
                            cookiesUsername = br.getCookie(br.getHost(), "email", Cookies.NOTDELETEDPATTERN);
                        }
                        /*
                         * During cookie login, user can enter whatever he wants into username field. Most users will enter their real
                         * username but to be sure to have unique usernames we don't trust them and try to get the real username out of our
                         * cookies.
                         */
                        if (!StringUtils.isEmpty(cookiesUsername) && !account.getUser().equals(cookiesUsername)) {
                            account.setUser(cookiesUsername);
                        }
                        return true;
                    } else {
                        logger.info("Cookie login failed");
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "Cookie login failed", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                } else if (this.requiresCookieLogin()) {
                    /* Ask user to login via exported browser cookies e.g. xubster.com. */
                    showCookieLoginInformation();
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "Cookie login required", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                int login_counter = 0;
                final int login_counter_max = 2;
                br.clearCookies(getMainPage());
                do {
                    login_counter++;
                    logger.info("Performing full website login attempt: " + login_counter);
                    Form loginForm = findLoginform(this.br);
                    if (loginForm == null) {
                        // some sites (eg filejoker) show login captcha AFTER first login attempt, so only reload getLoginURL(without
                        // captcha) if required
                        getPage(getLoginURL());
                        if (br.getHttpConnection().getResponseCode() == 404) {
                            /* Required for some XFS setups - use as common fallback. */
                            getPage(getMainPage() + "/login");
                        }
                        loginForm = findLoginform(this.br);
                        if (loginForm == null) {
                            logger.warning("Failed to find loginform");
                            /* E.g. 503 error during login */
                            checkResponseCodeErrors(br.getHttpConnection());
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                    }
                    if (loginForm.hasInputFieldByName("email")) {
                        /* 2019-08-16: Very rare case e.g. filejoker.net, filefox.cc */
                        loginForm.put("email", Encoding.urlEncode(account.getUser()));
                    } else {
                        loginForm.put("login", Encoding.urlEncode(account.getUser()));
                    }
                    loginForm.put("password", Encoding.urlEncode(account.getPass()));
                    /* Handle login-captcha if required */
                    final DownloadLink dlinkbefore = this.getDownloadLink();
                    try {
                        final DownloadLink dl_dummy;
                        if (dlinkbefore != null) {
                            dl_dummy = dlinkbefore;
                        } else {
                            dl_dummy = new DownloadLink(this, "Account", this.getHost(), "https://" + account.getHoster(), true);
                            this.setDownloadLink(dl_dummy);
                        }
                        handleCaptcha(dl_dummy, loginForm);
                    } finally {
                        this.setDownloadLink(dlinkbefore);
                    }
                    submitForm(loginForm);
                    if (!this.allows_multiple_login_attempts_in_one_go()) {
                        break;
                    }
                } while (!this.isLoggedin() && login_counter <= login_counter_max);
                if (!this.isLoggedin()) {
                    if (correctedBR.contains("op=resend_activation")) {
                        /* User entered correct logindata but hasn't activated his account yet. */
                        throw new AccountUnavailableException("\r\nYour account has not yet been activated!\r\nActivate it via the URL you received via E-Mail and try again!", 5 * 60 * 1000l);
                    }
                    if (this.allows_multiple_login_attempts_in_one_go()) {
                        logger.info("Login failed although there were two attempts");
                    } else {
                        logger.info("Login failed - check if the website needs a captcha after the first attempt so the plugin might have to be modified via allows_multiple_login_attempts_in_one_go");
                    }
                    /*
                     * TODO 2020-04-20: Modify text and only include the "or login captcha" part if user was actually asked to enter a login
                     * captcha or completely remove that.
                     */
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername, Passwort oder login Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłędny użytkownik/hasło lub kod Captcha wymagany do zalogowania!\r\nUpewnij się, że prawidłowo wprowadziłes hasło i nazwę użytkownika. Dodatkowo:\r\n1. Jeśli twoje hasło zawiera znaki specjalne, zmień je (usuń) i spróbuj ponownie!\r\n2. Wprowadź hasło i nazwę użytkownika ręcznie bez użycia opcji Kopiuj i Wklej.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or login captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(br.getCookies(getMainPage()), "");
                return true;
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            } finally {
                br.setFollowRedirects(followRedirects);
            }
        }
    }

    private Thread showCookieLoginInformation() {
        final String host = this.getHost();
        final Thread thread = new Thread() {
            public void run() {
                try {
                    final String help_article_url = "https://support.jdownloader.org/Knowledgebase/Article/View/account-cookie-login-instructions";
                    String message = "";
                    final String title;
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        title = host + " - Login";
                        message += "Hallo liebe(r) " + host + " NutzerIn\r\n";
                        message += "Um deinen " + host + " Account in JDownloader verwenden zu können, musst du folgende Schritte beachten:\r\n";
                        message += "Folge der Anleitung im Hilfe-Artikel:\r\n";
                        message += help_article_url;
                    } else {
                        title = host + " - Login";
                        message += "Hello dear " + host + " user\r\n";
                        message += "In order to use an account of this service in JDownloader, you need to follow these instructions:\r\n";
                        message += help_article_url;
                    }
                    final ConfirmDialog dialog = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, title, message);
                    dialog.setTimeout(3 * 60 * 1000);
                    if (CrossSystem.isOpenBrowserSupported() && !Application.isHeadless()) {
                        CrossSystem.openURL(help_article_url);
                    }
                    final ConfirmDialogInterface ret = UIOManager.I().show(ConfirmDialogInterface.class, dialog);
                    ret.throwCloseExceptions();
                } catch (final Throwable e) {
                    getLogger().log(e);
                }
            };
        };
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 2019-05-29: This is only EXPERIMENTAL! App-login: https://play.google.com/store/apps/details?id=net.sibsoft.xfsuploader <br/>
     * Around 2016 this has been implemented for some XFS websites but was never really used.It will return an XML response. Fragments of it
     * may still work for some XFS websites e.g. official DEMO website 'xfilesharing.com' and also 'europeup.com'. The login-cookie we get
     * is valid for the normal website as well! Biggest downside: Whenever a login-captcha is required (e.g. on too many wrong logins), this
     * method will NOT work!! <br/>
     * It seems like all or most of all XFS websites support this way of logging-in - even websites which were never officially supported
     * via XFS app (e.g. fileup.cc).
     */
    @Deprecated
    protected final boolean loginAPP(final Account account, boolean validateCookies) throws Exception {
        synchronized (account) {
            try {
                br.setHeader("User-Agent", "XFS-Mobile");
                br.setHeader("Content-Type", "application/x-www-form-urlencoded");
                /* Load cookies */
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                boolean validatedLoginCookies = false;
                /* 2019-08-29: Cookies will become invalid very soon so let's always verify them! */
                validateCookies = true;
                if (cookies != null) {
                    br.setCookies(getMainPage(), cookies);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= 300000l && !validateCookies) {
                        /* We trust these cookies as they're not that old --> Do not check them */
                        logger.info("Trust cookies without checking as they're still fresh");
                        return false;
                    }
                    logger.info("Verifying login-cookies");
                    getPage(getMainPage() + "/");
                    /* Missing login cookies? --> Login failed */
                    validatedLoginCookies = StringUtils.isEmpty(br.getCookie(getMainPage(), "xfss", Cookies.NOTDELETEDPATTERN));
                }
                if (validatedLoginCookies) {
                    /* No additional check required --> We know cookies are valid and we're logged in --> Done! */
                    logger.info("Successfully logged in via cookies");
                } else {
                    logger.info("Performing full login");
                    br.clearCookies(getMainPage());
                    final Form loginform = new Form();
                    loginform.setMethod(MethodType.POST);
                    loginform.setAction(getMainPage());
                    loginform.put("op", "api_get_limits");
                    loginform.put("login", Encoding.urlEncode(account.getUser()));
                    loginform.put("password", Encoding.urlEncode(account.getPass()));
                    submitForm(loginform);
                    /*
                     * Returns XML: ExtAllowed, ExtNotAllowed, MaxUploadFilesize, ServerURL[for uploads], SessionID[our login cookie],
                     * Error, SiteName, LoginLogic
                     */
                    /* Missing login cookies? --> Login failed */
                    if (StringUtils.isEmpty(br.getCookie(getMainPage(), "xfss", Cookies.NOTDELETEDPATTERN))) {
                        if (correctedBR.contains("op=resend_activation")) {
                            /* User entered correct logindata but has not activated his account ... */
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nYour account has not yet been activated!\r\nActivate it via the URL you should have received via E-Mail and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // /* Returns ballance, space, days(?premium days remaining?) - this call is not supported by all XFS sites - in this case
                // it'll return 404. */
                // final Form statsform = new Form();
                // statsform.setMethod(MethodType.POST);
                // statsform.setAction(getMainPage() + "/cgi-bin/uapi.cgi");
                // statsform.put("op", "api_get_stat");
                // submitForm(statsform);
                // final String spaceUsed = br.getRegex("<space>(\\d+\\.\\d+GB)</space>").getMatch(0);
                // final String balance = br.getRegex("<ballance>\\$(\\d+)</ballance>").getMatch(0);
                // // final String days = br.getRegex("<days>(\\d+)</days>").getMatch(0);
                // if (spaceUsed != null) {
                // account.getAccountInfo().setUsedSpace(SizeFormatter.getSize(spaceUsed));
                // }
                // if (balance != null) {
                // account.getAccountInfo().setAccountBalance(balance);
                // }
                account.saveCookies(br.getCookies(getMainPage()), "");
                validatedLoginCookies = true;
                return validatedLoginCookies;
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    protected boolean isAccountLoginVerificationEnabled(final Account account, final boolean verifiedLogin) {
        return !verifiedLogin;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        if (this.enable_account_api_only_mode()) {
            /* API mode */
            String dllink = checkDirectLink(link, directlinkproperty);
            if (StringUtils.isEmpty(dllink)) {
                dllink = this.getDllinkAPI(link, account);
            }
            handleDownload(link, account, dllink, null);
        } else {
            /* Website mode (might sometimes also perform some API requests) */
            if (AccountType.FREE.equals(account.getType())) {
                /*
                 * Perform linkcheck without logging in. TODO: Remove this and check for offline later as this would save one http request.
                 */
                requestFileInformationWebsite(link, account, true);
                final boolean verifiedLogin = loginWebsite(account, false);
                /* Access main Content-URL */
                this.getPage(link.getPluginPatternMatcher());
                if (isAccountLoginVerificationEnabled(account, verifiedLogin) && !isLoggedin()) {
                    loginWebsite(account, true);
                    getPage(link.getPluginPatternMatcher());
                }
                doFree(link, account);
            } else {
                /*
                 * 2019-05-21: TODO: Maybe try download right away instead of checking this here --> This could speed-up the
                 * download-start-procedure!
                 */
                String dllink = checkDirectLink(link, directlinkproperty);
                if (StringUtils.isEmpty(dllink)) {
                    /* First API --> This will also do linkcheck but only require one http request */
                    try {
                        dllink = this.getDllinkAPI(link, account);
                    } catch (final Throwable e) {
                        /* Do not allow exception to happen --> Fallback to website instead */
                        logger.log(e);
                        logger.warning("Error in API download handling");
                    }
                    /* API failed/not supported? Try website! */
                    if (StringUtils.isEmpty(dllink)) {
                        /* TODO: Maybe skip this, check for offline later */
                        requestFileInformationWebsite(link, account, true);
                        br.setFollowRedirects(false);
                        final boolean verifiedLogin = loginWebsite(account, false);
                        getPage(link.getPluginPatternMatcher());
                        if (isAccountLoginVerificationEnabled(account, verifiedLogin) && !isLoggedin()) {
                            loginWebsite(account, true);
                            getPage(link.getPluginPatternMatcher());
                        }
                        /*
                         * Check for final downloadurl here because if user/host has direct downloads enabled, PluginPatternMatcher will
                         * redirect to our final downloadurl thus isLoggedin might return false although we are loggedin!
                         */
                        /*
                         * First check for official video download as this is sometimes only available via account (example:
                         * xvideosharing.com)!
                         */
                        dllink = getDllinkViaOfficialVideoDownload(this.br.cloneBrowser(), link, account, false);
                        if (StringUtils.isEmpty(dllink)) {
                            dllink = getDllink(link, account);
                        }
                        if (StringUtils.isEmpty(dllink)) {
                            final Form dlForm = findFormDownload2Premium();
                            if (dlForm == null) {
                                checkErrors(link, account, true);
                                logger.warning("Failed to find Form download2");
                                checkErrorsLastResort(account);
                            }
                            handlePassword(dlForm, link);
                            final URLConnectionAdapter formCon = br.openFormConnection(dlForm);
                            if (isDownloadableContent(formCon)) {
                                /* Very rare case - e.g. tiny-files.com */
                                handleDownload(link, account, dllink, formCon.getRequest());
                                return;
                            } else {
                                try {
                                    br.followConnection(true);
                                } catch (IOException e) {
                                    logger.log(e);
                                }
                                this.correctBR();
                            }
                            checkErrors(link, account, true);
                            dllink = getDllink(link, account);
                        }
                    }
                }
                handleDownload(link, account, dllink, null);
            }
        }
    }

    protected boolean isDownloadableContent(URLConnectionAdapter con) throws IOException {
        return looksLikeDownloadableContent(con);
    }

    protected void handleDownload(final DownloadLink link, final Account account, String dllink, final Request req) throws Exception {
        final boolean resume = this.isResumeable(link, account);
        int maxChunks = getMaxChunks(account);
        if (maxChunks > 1) {
            logger.info("@Developer: fixme! maxChunks may not be fixed positive:" + maxChunks);
            maxChunks = -maxChunks;
        }
        if (req != null) {
            logger.info("Final downloadlink = Form download");
            /*
             * Save directurl before download-attempt as it should be valid even if it e.g. fails because of server issue 503 (= too many
             * connections) --> Should work fine after the next try.
             */
            final String location = req.getLocation();
            if (location != null) {
                /* E.g. redirect to downloadurl --> We can save that URL */
                storeDirecturl(link, account, location);
            }
            dl = new jd.plugins.BrowserAdapter().openDownload(br, link, req, resume, maxChunks);
            handleDownloadErrors(dl.getConnection(), link, account);
            try {
                fixFilename(link);
            } catch (Exception e) {
                logger.log(e);
            }
            try {
                /* add a download slot */
                controlMaxFreeDownloads(account, link, +1);
                /* start the dl */
                dl.startDownload();
            } finally {
                /* remove download slot */
                controlMaxFreeDownloads(account, link, -1);
            }
        } else {
            if (StringUtils.isEmpty(dllink) || (!dllink.startsWith("http") && !dllink.startsWith("rtmp") && !dllink.startsWith("/"))) {
                logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                checkErrorsLastResort(account);
            }
            logger.info("Final downloadlink = " + dllink + " starting the download...");
            if (dllink.startsWith("rtmp")) {
                /* 2019-05-21: rtmp download - VERY rare case! */
                try {
                    dl = new RTMPDownload(this, link, dllink);
                } catch (final NoClassDefFoundError e) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, "RTMPDownload class missing");
                }
                final String playpath = new Regex(dllink, "(mp4:.+)").getMatch(0);
                /* Setup rtmp connection */
                jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
                rtmp.setPageUrl(link.getPluginPatternMatcher());
                rtmp.setUrl(dllink);
                if (playpath != null) {
                    rtmp.setPlayPath(playpath);
                }
                rtmp.setFlashVer("WIN 25,0,0,148");
                rtmp.setSwfVfy("CHECK_ME");
                rtmp.setApp("vod/");
                rtmp.setResume(false);
                try {
                    fixFilename(link);
                } catch (Exception e) {
                    logger.log(e);
                }
                try {
                    /* add a download slot */
                    controlMaxFreeDownloads(account, link, +1);
                    /* start the dl */
                    ((RTMPDownload) dl).startDownload();
                } finally {
                    /* remove download slot */
                    controlMaxFreeDownloads(account, link, -1);
                }
            } else if (dllink.contains(".m3u8")) {
                /* 2019-08-29: HLS download - more and more streaming-hosts have this (example: streamty.com, vidlox.me) */
                dllink = handleQualitySelectionHLS(dllink);
                checkFFmpeg(link, "Download a HLS Stream");
                dl = new HLSDownloader(link, br, dllink);
                try {
                    /* add a download slot */
                    controlMaxFreeDownloads(account, link, +1);
                    /* start the dl */
                    dl.startDownload();
                } finally {
                    /* remove download slot */
                    controlMaxFreeDownloads(account, link, -1);
                }
            } else {
                dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, resume, maxChunks);
                /*
                 * Save directurl before download-attempt as it should be valid even if it e.g. fails because of server issue 503 (= too
                 * many connections) --> Should work fine after the next try.
                 */
                storeDirecturl(link, account, dl.getConnection().getURL().toString());
                handleDownloadErrors(dl.getConnection(), link, account);
                try {
                    fixFilename(link);
                } catch (Exception e) {
                    logger.log(e);
                }
                try {
                    /* add a download slot */
                    controlMaxFreeDownloads(account, link, +1);
                    /* start the dl */
                    dl.startDownload();
                } finally {
                    /* remove download slot */
                    controlMaxFreeDownloads(account, link, -1);
                }
            }
        }
    }

    /** Returns user selected streaming quality. Returns BEST by default / no selection. */
    private String handleQualitySelectionHLS(final String hls_master) throws Exception {
        if (hls_master == null) {
            /* This should never happen */
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final Browser brc = br.cloneBrowser();
        this.getPage(brc, hls_master);
        final List<HlsContainer> hlsQualities = HlsContainer.getHlsQualities(brc);
        if (hlsQualities == null) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown HLS streaming error");
        }
        HlsContainer hlsSelected = null;
        final int userSelectedQuality = getPreferredStreamQuality();
        if (userSelectedQuality == -1) {
            logger.info("Looking for BEST video stream");
            hlsSelected = HlsContainer.findBestVideoByBandwidth(hlsQualities);
        } else {
            logger.info("Looking for user selected video stream quality: " + userSelectedQuality);
            for (final HlsContainer hlsQualityTmp : hlsQualities) {
                /*
                 * TODO: Check if they're always the same or if they can also be crooked numbers. See ZDFMediathekDecrypter -->
                 * getHeightForQualitySelection()
                 */
                final int height = hlsQualityTmp.getHeight();
                if (height == userSelectedQuality) {
                    logger.info("Successfully found selected quality: " + userSelectedQuality);
                    hlsSelected = hlsQualityTmp;
                    break;
                }
            }
            if (hlsSelected == null) {
                logger.info("Failed to find user selected quality --> Returning BEST instead");
                hlsSelected = HlsContainer.findBestVideoByBandwidth(hlsQualities);
            }
        }
        logger.info(String.format("Picked stream quality = %sp", hlsSelected.getHeight()));
        return hlsSelected.getDownloadurl();
    }

    /** Stores final downloadurl on current DownloadLink object */
    protected void storeDirecturl(final DownloadLink link, final Account account, final String directurl) {
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        link.setProperty(directlinkproperty, directurl);
    }

    /** Handles errors right before starting the download. */
    protected void handleDownloadErrors(URLConnectionAdapter con, final DownloadLink link, final Account account) throws Exception {
        if (!isDownloadableContent(con)) {
            logger.warning("The final dllink seems not to be a file!");
            try {
                br.followConnection(true);
            } catch (IOException e) {
                logger.log(e);
            }
            correctBR();
            checkResponseCodeErrors(con);
            checkServerErrors(link, account);
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Final downloadlink did not lead to downloadable content");
        } else {
            try {
                checkResponseCodeErrors(con);
            } catch (PluginException e) {
                try {
                    br.followConnection(true);
                } catch (IOException ioe) {
                    throw Exceptions.addSuppressed(e, ioe);
                }
                throw e;
            }
        }
    }

    /* *************************** PUT API RELATED METHODS HERE *************************** */
    protected String getAPIBase() {
        final String custom_apidomain = this.getPluginConfig().getStringProperty(PROPERTY_PLUGIN_api_domain_with_protocol);
        if (custom_apidomain != null) {
            return custom_apidomain;
        } else {
            return getMainPage() + "/api";
        }
    }

    /** Generates final downloadurl via API if API usage is allowed and apikey is available. */
    protected String getDllinkAPI(final DownloadLink link, final Account account) throws Exception {
        String dllink = null;
        /**
         * Only execute this if you know that the currently used host supports this! </br>
         * Only execute this if an apikey is given! </br>
         * Only execude this if you know that a particular host has enabled this API call! </br>
         * Important: For some hosts, this API call will only be available for premium accounts, no for free accounts!
         */
        if (this.enable_account_api_only_mode() || this.allow_api_download_if_apikey_is_available(account)) {
            /* 2019-11-04: Linkcheck is not required here - download API will return offline status. */
            // requestFileInformationAPI(link, account);
            logger.info("Trying to get dllink via API");
            final String apikey = getAPIKeyFromAccount(account);
            if (StringUtils.isEmpty(apikey)) {
                /* This should never happen */
                logger.warning("Cannot do this without apikey");
                return null;
            }
            final String fileid_to_download;
            if (requires_api_getdllink_clone_workaround(account)) {
                logger.info("Trying to download file via clone workaround");
                getPage(this.getAPIBase() + "/file/clone?key=" + apikey + "&file_code=" + this.getFUIDFromURL(link));
                this.checkErrorsAPI(this.br, link, account);
                fileid_to_download = PluginJSonUtils.getJson(br, "filecode");
                if (StringUtils.isEmpty(fileid_to_download)) {
                    logger.warning("Failed to find new fileid in clone handling");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            } else {
                logger.info("Trying to download file via api without workaround");
                fileid_to_download = this.getFUIDFromURL(link);
            }
            /*
             * Users can also chose a preferred quality via '&q=h' but we prefer to receive all and then chose to easily have a fallback in
             * case the quality selected by our user is not available.
             */
            /* Documentation videohost: https://xfilesharingpro.docs.apiary.io/#reference/file/file-clone/get-direct-link */
            /*
             * Documentation filehost:
             * https://xvideosharing.docs.apiary.io/#reference/file/file-direct-link/get-links-to-all-available-qualities
             */
            getPage(this.getAPIBase() + "/file/direct_link?key=" + apikey + "&file_code=" + fileid_to_download);
            this.checkErrorsAPI(this.br, link, account);
            LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
            LinkedHashMap<String, Object> entries_tmp;
            entries = (LinkedHashMap<String, Object>) entries.get("result");
            /**
             * TODO: Add quality selection. 2020-05-20: Did not add selection yet because so far this API call has NEVER worked for ANY
             * filehost&videohost!
             */
            /* For videohosts: Pick the best quality */
            final String[] qualities = new String[] { "o", "h", "n", "l" };
            for (final String quality : qualities) {
                final Object qualityO = entries.get(quality);
                if (qualityO != null) {
                    entries_tmp = (LinkedHashMap<String, Object>) qualityO;
                    dllink = (String) entries_tmp.get("url");
                    break;
                }
            }
            if (StringUtils.isEmpty(dllink)) {
                /* For filehosts (= no different qualities available) */
                logger.info("Failed to find any quality - downloading original file");
                dllink = (String) entries.get("url");
                // final long filesize = JavaScriptEngineFactory.toLong(entries.get("size"), 0);
            }
            if (dllink != null) {
                logger.info("Successfully found dllink via API");
            } else {
                logger.warning("Failed to find dllink via API");
                this.checkErrorsAPI(br, link, account);
                /**
                 * TODO: Check if defect message makes sense here. Once we got better errorhandling we can eventually replace this with a
                 * waittime.
                 */
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        return dllink;
    }

    /**
     * Advantages over website: <br/>
     * - Always precise expire-date <br/>
     * - All info we need via one single http request <br/>
     * - Consistent
     */
    protected AccountInfo fetchAccountInfoAPI(final Browser br, final Account account) throws Exception {
        /*
         * 2020-03-20: TODO: Check if more XFS sites include 'traffic_left' and 'premium_traffic_left' here and implement it. See Plugins
         * ShareOnlineTo and DdlTo
         */
        final AccountInfo ai = new AccountInfo();
        loginAPI(br, account);
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
        /** 2019-07-31: Better compare expire-date against their serverside time if possible! */
        final String server_timeStr = (String) entries.get("server_time");
        entries = (LinkedHashMap<String, Object>) entries.get("result");
        long expire_milliseconds_precise_to_the_second = 0;
        String email = (String) entries.get("email");
        final long currentTime;
        if (server_timeStr != null && server_timeStr.matches("\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            currentTime = TimeFormatter.getMilliSeconds(server_timeStr, "yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        } else {
            /* Fallback */
            currentTime = System.currentTimeMillis();
        }
        /*
         * 2019-05-30: Seems to be a typo by the guy who develops the XFS script in the early versions of thei "API mod" :D 2019-07-28: Typo
         * is fixed in newer XFSv3 versions - still we'll keep both versions in just to make sure it will always work ...
         */
        String expireStr = (String) entries.get("premim_expire");
        if (StringUtils.isEmpty(expireStr)) {
            /* Try this too in case he corrects his mistake. */
            expireStr = (String) entries.get("premium_expire");
        }
        /*
         * 2019-08-22: For newly created free accounts, an expire-date will always be given, even if the account has never been a premium
         * account. This expire-date will usually be the creation date of the account then --> Handling will correctly recognize it as a
         * free account!
         */
        if (expireStr != null && expireStr.matches("\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            expire_milliseconds_precise_to_the_second = TimeFormatter.getMilliSeconds(expireStr, "yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        }
        /*
         * 2019-08-22: Sadly there is no "traffic_left" value given. Upper handling will try to find it via website. Because we access
         * account-info page anyways during account-check we at least don't have to waste another http-request for that.
         */
        ai.setUnlimitedTraffic();
        final long premiumDurationMilliseconds = expire_milliseconds_precise_to_the_second - currentTime;
        if (premiumDurationMilliseconds <= 0) {
            /* Expired premium or no expire date given --> It is usually a Free Account */
            setAccountLimitsByType(account, AccountType.FREE);
        } else {
            /* Expire date is in the future --> It is a premium account */
            ai.setValidUntil(System.currentTimeMillis() + premiumDurationMilliseconds);
            setAccountLimitsByType(account, AccountType.PREMIUM);
        }
        {
            /* Now set less relevant account information */
            final long balance = JavaScriptEngineFactory.toLong(entries.get("balance"), -1);
            /* 2019-07-26: values can also be "inf" for "Unlimited": "storage_left":"inf" */
            // final long storage_left = JavaScriptEngineFactory.toLong(entries.get("storage_left"), 0);
            final long storage_used_bytes = JavaScriptEngineFactory.toLong(entries.get("storage_used"), -1);
            if (storage_used_bytes > -1) {
                ai.setUsedSpace(storage_used_bytes);
            }
            if (balance > -1) {
                ai.setAccountBalance(balance);
            }
        }
        if (this.enable_account_api_only_mode() && !StringUtils.isEmpty(email)) {
            /*
             * Each account is unique. Do not care what the user entered - trust what API returns! </br> This is not really important - more
             * visually so that something that makes sense is displayed to the user in his account managers' "Username" column!
             */
            account.setUser(email);
        }
        return ai;
    }

    /**
     * More info see supports_api()
     */
    protected final void loginAPI(final Browser br, final Account account) throws Exception {
        synchronized (account) {
            final boolean followRedirects = br.isFollowingRedirects();
            try {
                br.setCookiesExclusive(true);
                final String apikey = this.getAPIKeyFromAccount(account);
                if (!this.isAPIKey(apikey)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid apikey format!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                getPage(br, this.getAPIBase() + "/account/info?key=" + apikey);
                final String msg = PluginJSonUtils.getJson(br, "msg");
                final String status = PluginJSonUtils.getJson(br, "status");
                /* 2019-05-30: There are no cookies at all (only "__cfduid" [Cloudflare cookie] sometimes.) */
                final boolean jsonOK = msg != null && msg.equalsIgnoreCase("ok") && status != null && status.equals("200");
                if (!jsonOK) {
                    /* E.g. {"msg":"Wrong auth","server_time":"2019-05-29 19:29:03","status":403} */
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            } finally {
                br.setFollowRedirects(followRedirects);
            }
        }
    }

    protected final AvailableStatus requestFileInformationAPI(final DownloadLink link, final String apikey) throws Exception {
        massLinkcheckerAPI(new DownloadLink[] { link }, apikey, false);
        if (link.getAvailableStatus() == AvailableStatus.FALSE) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return link.getAvailableStatus();
    }

    /**
     * Checks multiple URLs via API. Only works when an apikey is given!
     */
    public boolean massLinkcheckerAPI(final DownloadLink[] urls, final String apikey, final boolean allowWeakFilenameAsFallback) {
        if (urls == null || urls.length == 0 || !this.isAPIKey(apikey)) {
            return false;
        }
        boolean linkcheckerHasFailed = false;
        try {
            final Browser br = new Browser();
            this.prepBrowser(br, getMainPage());
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                links.clear();
                while (true) {
                    /*
                     * We test max 50 links at once. 2020-05-29: XFS default API linkcheck limit is exactly 50 items. If you check more than
                     * 50 items, it will only return results for the first 50 items.
                     */
                    if (index == urls.length || links.size() == 50) {
                        break;
                    } else {
                        links.add(urls[index]);
                        index++;
                    }
                }
                sb.delete(0, sb.capacity());
                for (final DownloadLink dl : links) {
                    // sb.append("%0A");
                    sb.append(this.getFUIDFromURL(dl));
                    sb.append("%2C");
                }
                getPage(br, getAPIBase() + "/file/info?key=" + apikey + "&file_code=" + sb.toString());
                try {
                    this.checkErrorsAPI(br, links.get(0), null);
                } catch (final Throwable e) {
                    logger.log(e);
                    /* E.g. invalid apikey, broken serverside API. */
                    logger.info("Fatal failure");
                    return false;
                }
                LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
                final ArrayList<Object> ressourcelist = (ArrayList<Object>) entries.get("result");
                for (final DownloadLink link : links) {
                    boolean foundResult = false;
                    for (final Object fileO : ressourcelist) {
                        entries = (LinkedHashMap<String, Object>) fileO;
                        final String fuid_temp = (String) entries.get("filecode");
                        if (fuid_temp != null && fuid_temp.equalsIgnoreCase(this.getFUIDFromURL(link))) {
                            foundResult = true;
                            break;
                        }
                    }
                    if (!foundResult) {
                        /**
                         * This should never happen. Possible reasons: </br>
                         * - Wrong APIKey </br>
                         * - We tried to check too many items at once </br>
                         * - API only allows users to check self-uploaded content --> Disable API linkchecking in plugin! </br>
                         * - API does not not allow linkchecking at all --> Disable API linkchecking in plugin! </br>
                         */
                        logger.warning("WTF failed to find information for fuid: " + this.getFUIDFromURL(link));
                        linkcheckerHasFailed = true;
                        continue;
                    }
                    /* E.g. check for "result":[{"status":404,"filecode":"xxxxxxyyyyyy"}] */
                    final long status = JavaScriptEngineFactory.toLong(entries.get("status"), 404);
                    if (status != 200) {
                        link.setAvailable(false);
                        if (!link.isNameSet()) {
                            setWeakFilename(link);
                        }
                    } else {
                        link.setAvailable(true);
                        String filename = (String) entries.get("name");
                        final long filesize = JavaScriptEngineFactory.toLong(entries.get("size"), 0);
                        final Object canplay = entries.get("canplay");
                        final Object views_started = entries.get("views_started");
                        final Object views = entries.get("views");
                        final Object length = entries.get("length");
                        final boolean isVideohost = canplay != null || views_started != null || views != null || length != null;
                        if (!StringUtils.isEmpty(filename)) {
                            /*
                             * At least for videohosts, filenames from json would often not contain a file extension!
                             */
                            if (Encoding.isHtmlEntityCoded(filename)) {
                                filename = Encoding.htmlDecode(filename);
                            }
                            if (isVideohost && !filename.endsWith(".mp4")) {
                                filename += ".mp4";
                            }
                            link.setFinalFileName(filename);
                        } else {
                            if (isVideohost) {
                                link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
                            }
                            if (allowWeakFilenameAsFallback && !link.isNameSet()) {
                                setWeakFilename(link);
                            }
                        }
                        /* Filesize is not always given especially not for videohosts. */
                        if (filesize > 0) {
                            link.setDownloadSize(filesize);
                        }
                    }
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            logger.log(e);
            return false;
        } finally {
            if (linkcheckerHasFailed) {
                logger.info("Seems like massLinkcheckerAPI availablecheck is not supported by this host");
                this.getPluginConfig().setProperty("MASS_LINKCHECKER_API_LAST_FAILURE_TIMESTAMP", System.currentTimeMillis());
            }
        }
        if (linkcheckerHasFailed) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Can be executed after API calls to check for- and handle errors. </br>
     * Example good API response: {"msg":"OK","server_time":"2020-05-25 13:09:37","status":200,"result":[{"...
     */
    protected void checkErrorsAPI(final Browser br, final DownloadLink link, final Account account) throws NumberFormatException, PluginException {
        /**
         * 2019-10-31: TODO: Add support for more errorcodes e.g. downloadlimit reached, premiumonly, password protected, wrong password,
         * wrong captcha. [PW protected + captcha protected download handling is not yet implemented serverside]
         */
        String errorCodeStr = null;
        String errorMsg = null;
        int errorcode = -1;
        try {
            final Map<String, Object> entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            final Object statusO = entries.get("status");
            if (statusO instanceof String) {
                errorCodeStr = (String) statusO;
            } else {
                errorcode = ((Number) statusO).intValue();
            }
            errorMsg = (String) entries.get("msg");
        } catch (final Throwable e) {
            logger.log(e);
            logger.info("API json parsing error");
        }
        if (StringUtils.isEmpty(errorMsg)) {
            errorMsg = "Unknown error";
        }
        switch (errorcode) {
        case -1:
            /* No error */
            break;
        case 200:
            /* No error */
            break;
        case 400:
            /* {"msg":"Invalid key","server_time":"2019-10-31 17:20:02","status":400} */
            /*
             * This should never happen!
             */
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid apikey!\r\nEntered apikey does not match expected format.", PluginException.VALUE_ID_PREMIUM_DISABLE);
        case 403:
            if (errorMsg.equalsIgnoreCase("This function not allowed in API")) {
                /* {"msg":"This function not allowed in API","server_time":"2019-10-31 17:02:31","status":403} */
                /* This should never happen! Plugin needs to be */
                if (link == null) {
                    /*
                     * Login via API either not supported at all (wtf why is there an apikey available) or only for special/unlocked users!
                     */
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAPI login impossible!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Unsupported API function - plugin might need update", 2 * 60 * 60 * 1000l);
                }
            } else {
                /* {"msg":"Wrong auth","server_time":"2019-10-31 16:54:05","status":403} */
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid or expired apikey!\r\nWhen changing your apikey via website, make sure to update it in JD too!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        case 404:
            /* {"msg":"No file","server_time":"2019-10-31 17:23:17","status":404} */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        default:
            /* Do not throw Exception here - usually website will be used as fallback and website-errors will be handled correctly */
            logger.info("Unknown API error: " + errorCodeStr);
            break;
        }
    }

    protected final String getAPIKeyFromAccount(final Account account) {
        synchronized (account) {
            final String apikey;
            if (this.enable_account_api_only_mode()) {
                /* In API only mode, apikey is stored in password field. */
                apikey = account.getPass();
            } else {
                /* In website mode we store apikey as a property on our current account object. */
                apikey = account.getStringProperty(PROPERTY_ACCOUNT_apikey, null);
            }
            if (isAPIKey(apikey)) {
                return apikey;
            } else {
                return null;
            }
        }
    }

    /** @return apikey but only if it is considered valid! */
    protected final String getAPIKeyFromConfig() {
        final Class<? extends XFSConfigVideo> cfgO = this.getConfigInterface();
        if (cfgO == null) {
            return null;
        } else {
            final String apikey = PluginJsonConfig.get(cfgO).getApikey();
            if (this.isAPIKey(apikey)) {
                return apikey;
            } else {
                return null;
            }
        }
    }

    /**
     * This will try to return an apikey, preferably from a valid account. </br>
     * Uses API key from config as fallback.
     */
    protected final String getAPIKey() {
        final Account acc = AccountController.getInstance().getValidAccount(this.getHost());
        if (acc != null && this.getAPIKeyFromAccount(acc) != null) {
            return this.getAPIKeyFromAccount(acc);
        } else {
            return this.getAPIKeyFromConfig();
        }
    }

    protected boolean isAPIKey(final String apiKey) {
        return apiKey != null && apiKey.matches("^[a-z0-9]{16,}$");
    }

    @Override
    public AccountBuilderInterface getAccountFactory(InputChangedCallbackInterface callback) {
        if (this.enable_account_api_only_mode()) {
            return new XFSApiAccountFactory(callback);
        } else {
            return new DefaultEditAccountPanel(callback, !getAccountwithoutUsername());
        }
    }

    public static class XFSApiAccountFactory extends MigPanel implements AccountBuilderInterface {
        private static final long serialVersionUID = 1L;
        private final String      PINHELP          = "Enter your API Key";

        private String getPassword() {
            if (this.pass == null) {
                return null;
            }
            if (EMPTYPW.equals(new String(this.pass.getPassword()))) {
                return null;
            }
            return new String(this.pass.getPassword());
        }

        public boolean updateAccount(Account input, Account output) {
            boolean changed = false;
            if (!StringUtils.equals(input.getUser(), output.getUser())) {
                output.setUser(input.getUser());
                changed = true;
            }
            if (!StringUtils.equals(input.getPass(), output.getPass())) {
                output.setPass(input.getPass());
                changed = true;
            }
            return changed;
        }

        private final ExtPasswordField pass;
        private static String          EMPTYPW = " ";
        private final JLabel           idLabel;

        public XFSApiAccountFactory(final InputChangedCallbackInterface callback) {
            super("ins 0, wrap 2", "[][grow,fill]", "");
            add(new JLabel("Click here to find your API Key:"));
            add(new JLink("https://examplehost.com/?op=my_account"));
            this.add(this.idLabel = new JLabel("Enter your API Key:"));
            add(this.pass = new ExtPasswordField() {
                @Override
                public void onChanged() {
                    callback.onChangedInput(this);
                }
            }, "");
            pass.setHelpText(PINHELP);
        }

        @Override
        public JComponent getComponent() {
            return this;
        }

        @Override
        public void setAccount(Account defaultAccount) {
            if (defaultAccount != null) {
                // name.setText(defaultAccount.getUser());
                pass.setText(defaultAccount.getPass());
            }
        }

        @Override
        public boolean validateInputs() {
            final String password = getPassword();
            if (password == null || !password.trim().matches("^[a-z0-9]{16,}$")) {
                idLabel.setForeground(Color.RED);
                return false;
            }
            idLabel.setForeground(Color.BLACK);
            return getPassword() != null;
        }

        @Override
        public Account getAccount() {
            return new Account(null, getPassword());
        }
    }

    /**
     * pseudo redirect control!
     */
    @Override
    protected void runPostRequestTask(Browser ibr) throws Exception {
        final String redirect;
        if (!ibr.isFollowingRedirects() && (redirect = ibr.getRedirectLocation()) != null) {
            if (!this.isImagehoster()) {
                if (!isDllinkFile(redirect)) {
                    super.getPage(ibr, redirect);
                    return;
                }
            } else {
                super.getPage(ibr, redirect);
                return;
            }
        }
    }

    /**
     * Use this to set filename based on filename inside URL or fuid as filename either before a linkcheck happens so that there is a
     * readable filename displayed in the linkgrabber or also for mass-linkchecking as in this case these is no filename given inside HTML.
     */
    protected void setWeakFilename(final DownloadLink link) {
        final String weak_fallback_filename = this.getFallbackFilename(link);
        if (weak_fallback_filename != null) {
            link.setName(weak_fallback_filename);
        }
        /*
         * Only set MineHint if: 1. No filename at all is set OR the given name does not contain any fileextension, AND 2. We know that the
         * filehost is only hosting specific data (audio, video, pictures)!
         */
        final boolean fallback_filename_contains_file_extension = weak_fallback_filename != null && weak_fallback_filename.contains(".");
        final boolean setMineHint = weak_fallback_filename == null || !fallback_filename_contains_file_extension;
        if (setMineHint) {
            /* Only setMimeHint if weak filename does not contain filetype. */
            if (this.isImagehoster()) {
                link.setMimeHint(CompiledFiletypeFilter.ImageExtensions.JPG);
            } else if (this.internal_isVideohoster_enforce_video_filename()) {
                link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
            }
        }
    }

    /** Returns empty StringArray for filename, filesize, filehash, [more information in the future?] */
    public final String[] internal_getFileInfoArray() {
        return new String[3];
    }

    /**
     * This can 'automatically' detect whether a host supports embedding videos. <br />
     * Example: uqload.com</br>
     * Do not override!
     */
    protected final boolean internal_isVideohosterEmbed() {
        return isVideohosterEmbed() || isVideohosterEmbedHTML();
    }

    /**
     * Decides whether to enforce a filename with a '.mp4' ending or not. </br>
     * Names are either enforced if the configuration of the script implies this or if it detects that embedding videos is possible. </br>
     * Do not override - at least try to avoid having to!!
     */
    private final boolean internal_isVideohoster_enforce_video_filename() {
        return internal_isVideohosterEmbed() || isVideohoster_enforce_video_filename();
    }

    @Override
    public boolean internal_supportsMassLinkcheck() {
        return this.supports_mass_linkcheck_over_api() || this.supports_mass_linkcheck_over_website() || this.enable_account_api_only_mode();
    }

    /**
     * Override this and let it return true whenever an user provided API key is available to allow the plugin to do single linkchecks via
     * API. </br>
     *
     * @default false
     */
    protected boolean supports_single_linkcheck_over_api() {
        // return isAPIKey(this.getAPIKey());
        /* On Override, you would typically use the above line of code as return value. */
        return false;
    }

    /** @default false */
    protected boolean supports_mass_linkcheck_over_api() {
        // return isAPIKey(this.getAPIKey());
        /* On Override, you would typically use the above line of code as return value. */
        return false;
    }

    /**
     * This can 'automatically' detect whether a host supports availablecheck via 'abuse' URL. <br />
     * Example: uploadboy.com</br>
     * Do not override - at least try to avoid having to!!
     */
    private final boolean internal_supports_availablecheck_filename_abuse() {
        final boolean supported_by_hardcoded_setting = this.supports_availablecheck_filename_abuse();
        final boolean supported_by_indicating_html_code = new Regex(correctedBR, "op=report_file&(?:amp;)?id=" + this.fuid).matches();
        boolean allowed_by_auto_handling = true;
        final long last_failure = this.getPluginConfig().getLongProperty("REPORT_FILE_AVAILABLECHECK_LAST_FAILURE_TIMESTAMP", 0);
        if (last_failure > 0) {
            final long timestamp_cooldown = last_failure + internal_waittime_on_alternative_availablecheck_failures();
            if (timestamp_cooldown > System.currentTimeMillis()) {
                logger.info("internal_supports_availablecheck_filename_abuse is still deactivated as it did not work on the last attempt");
                logger.info("Time until retry: " + TimeFormatter.formatMilliSeconds(timestamp_cooldown - System.currentTimeMillis(), 0));
                allowed_by_auto_handling = false;
            }
        }
        return (supported_by_hardcoded_setting || supported_by_indicating_html_code) && allowed_by_auto_handling;
    }

    private final boolean internal_supports_availablecheck_alt() {
        boolean allowed_by_auto_handling = true;
        final long last_failure = this.getPluginConfig().getLongProperty("ALT_AVAILABLECHECK_LAST_FAILURE_TIMESTAMP", 0);
        if (last_failure > 0) {
            final long timestamp_cooldown = last_failure + internal_waittime_on_alternative_availablecheck_failures();
            if (timestamp_cooldown > System.currentTimeMillis()) {
                logger.info("internal_supports_availablecheck_alt is still deactivated as it did not work on the last attempt");
                logger.info("Time until retry: " + TimeFormatter.formatMilliSeconds(timestamp_cooldown - System.currentTimeMillis(), 0));
                allowed_by_auto_handling = false;
            }
        }
        return supports_availablecheck_alt() && allowed_by_auto_handling;
    }

    /**
     * Defines the time to wait until a failed linkcheck method will be tried again. This should be set to > 24 hours as its purpose is to
     * minimize unnecessary http requests.
     */
    protected final long internal_waittime_on_alternative_availablecheck_failures() {
        return 7 * 24 * 60 * 60 * 1000;
    }

    /**
     * Function to check whether or not a filehost is running XFS API mod or not. Only works for APIs running on their main domain and not
     * any other/special domain! </br>
     * Example test working & API available: https://fastfile.cc/api/account/info </br>
     * Example not working but API available: https://api-v2.ddownload.com/api/account/info </br>
     * Example API not available (= XFS API Mod not installed): </br>
     */
    private boolean test_supports_api() throws IOException {
        br.getPage(this.getAPIBase() + "/account/info");
        /* 2020-05-29: Answer we'd expect if API is available: {"msg":"Invalid key","server_time":"2020-05-29 17:16:36","status":400} */
        final String msg = PluginJSonUtils.getJson(br, "msg");
        final String server_time = PluginJSonUtils.getJson(br, "server_time");
        if (!StringUtils.isEmpty(msg) && !StringUtils.isEmpty(server_time)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Class<? extends XFSConfigVideo> getConfigInterface() {
        return null;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.SibSoft_XFileShare;
    }
}
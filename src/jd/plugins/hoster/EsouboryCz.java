//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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
package jd.plugins.hoster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

import jd.PluginWrapper;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.MultiHosterManagement;
import jd.plugins.components.PluginJSonUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "esoubory.cz" }, urls = { "https?://(?:www\\.)?esoubory\\.cz/(?:[a-z]{2}/)?(?:file|soubor|redir)/[a-f0-9]{8}/[a-z0-9\\-]+(?:/?|\\.html)" })
public class EsouboryCz extends PluginForHost {
    public EsouboryCz(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.esoubory.cz/credits/buy/");
    }

    /* Using similar API (and same owner): esoubory.cz, filesloop.com */
    @Override
    public String getAGBLink() {
        return "http://www.esoubory.cz/";
    }

    private static final String          API_BASE                       = "https://www.esoubory.cz/api";
    /* 2018-12-27: API for selfhosted content is broken */
    private static final boolean         USE_API_FOR_SELFHOSTED_CONTENT = true;
    private static MultiHosterManagement mhm                            = new MultiHosterManagement("esoubory.cz");

    private void prepBr() {
        br.getHeaders().put("User-Agent", "JDownloader");
    }

    @Override
    public boolean canHandle(final DownloadLink link, final Account account) throws Exception {
        if (account != null) {
            return true;
        }
        return false;
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        final String linkpart = getLinkpart(link);
        if (linkpart != null) {
            link.setPluginPatternMatcher("https://www.esoubory.cz/soubor/" + linkpart + ".html");
        }
    }

    private String getLinkpart(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), "/([a-f0-9]{8}/[a-z0-9\\-]+)(?:/?|\\.html)").getMatch(0);
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = getFID(link);
        if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), "(?:file|soubor|redir)/([a-f0-9]{8})").getMatch(0);
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, Exception {
        final Account aa = AccountController.getInstance().getValidAccount(this);
        return requestFileInformation(link, aa);
    }

    public AvailableStatus requestFileInformation(final DownloadLink link, final Account account) throws IOException, Exception {
        br.setFollowRedirects(true);
        final Account aa = AccountController.getInstance().getValidAccount(this);
        final String name_url = new Regex(link.getPluginPatternMatcher(), "(?:file|soubor|redir)/[a-f0-9]{8}/([a-z0-9\\-]+)").getMatch(0);
        if (name_url != null) {
            link.setName(name_url);
        }
        String filename;
        String filesize;
        if (aa != null && USE_API_FOR_SELFHOSTED_CONTENT) {
            /* API */
            br.getPage(API_BASE + "/exists?token=" + getToken(aa) + "&url=" + Encoding.urlEncode(link.getDownloadURL()));
            if (!br.containsHTML("\"exists\":true")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = PluginJSonUtils.getJson(br, "filename");
            filename = Encoding.unicodeDecode(filename);
            filesize = PluginJSonUtils.getJson(br, "filesize");
            link.setDownloadSize(Long.parseLong(filesize));
            link.setFinalFileName(filename);
        } else {
            /* Website */
            br.getPage(link.getPluginPatternMatcher());
            if (br.getURL().contains("/search/") || br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            // final Regex linkinfo = br.getRegex("<h1>([^<>\"]*?)<span class=\"bluetext upper\">\\(([^<>\"]*?)\\)</span>");
            final Regex linkinfo = br.getRegex("<h1>\\s*([^<>]*?)\\((\\d+(,\\d+)? (K|M|G)B)\\)\\s*</h1>");
            filename = linkinfo.getMatch(0);
            filesize = linkinfo.getMatch(1);
            String fileextension = br.getRegex("<span class=\"fa fa\\-file\"></span>([^<>\"]+)</span>").getMatch(0);
            if (filename == null || filesize == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            filename = Encoding.htmlDecode(filename).trim();
            if (fileextension != null) {
                fileextension = fileextension.trim();
                if (!filename.endsWith(fileextension)) {
                    filename += fileextension;
                }
            }
            filesize = filesize.replace(",", ".");
            link.setDownloadSize(SizeFormatter.getSize(filesize));
            /* Do not set the final filename here as we'll have the API when downloading via account anyways! */
            link.setName(filename);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        handleDL(link, account);
    }

    @SuppressWarnings("deprecation")
    private void handleDL(final DownloadLink link, final Account account) throws Exception {
        String finallink = checkDirectLink(link, "esouborydirectlink");
        final boolean isSelfhostedContent = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).matches();
        if (finallink == null) {
            if (isSelfhostedContent && !USE_API_FOR_SELFHOSTED_CONTENT) {
                /* 2018-12-27: API Support broken for selfhosted content! */
                /* 2020-05-12: API is working fine again for selfhosted content */
                loginWebsite(account, false);
                requestFileInformation(link, account);
                br.setFollowRedirects(true);
                /* Downloadlink has to be accessed otherwise we're not able to download via 'finallink' below! */
                br.getPage(link.getPluginPatternMatcher());
                finallink = "https://www." + this.getHost() + "/redir/" + getLinkpart(link) + ".html";
                // br.setFollowRedirects(false);
                // final String continue_url = "https://www.esoubory.cz/redir/" + new Regex(link.getPluginPatternMatcher(),
                // "([^/]+/[^/]+)(?:\\.html)?$").getMatch(0) + ".html";
                // br.getPage(continue_url);
                // finallink = br.getRedirectLocation();
            } else {
                if (isSelfhostedContent) {
                    requestFileInformation(link, account);
                }
                br.getPage(API_BASE + "/filelink?token=" + getToken(account) + "&url=" + Encoding.urlEncode(link.getDownloadURL()));
                if (br.containsHTML("\"error\":\"not\\-enough\\-credits\"")) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                }
                finallink = PluginJSonUtils.getJson(br, "link");
            }
        }
        if (StringUtils.isEmpty(finallink)) {
            logger.warning("Failed to find final downloadlink");
            if (isSelfhostedContent) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            } else {
                mhm.handleErrorGeneric(account, link, "Failed to find final downloadurl", 50);
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, finallink, true, -2);
        if (!dl.getConnection().isContentDisposition()) {
            br.followConnection();
            if (isSelfhostedContent) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            } else {
                mhm.handleErrorGeneric(account, link, "Unknown download error", 50);
            }
        }
        link.setProperty("esouborydirectlink", finallink);
        dl.startDownload();
    }

    /** 2018-12-27: Required for some parts of the plugin for which the API fails. */
    private void loginWebsite(final Account account, final boolean forceVerify) throws IOException, PluginException {
        br.setFollowRedirects(true);
        final Cookies cookies = account.loadCookies("");
        if (cookies != null) {
            logger.info("Attempting cookie login");
            br.setCookies(this.getHost(), cookies);
            if (!forceVerify && System.currentTimeMillis() - account.getCookiesTimeStamp("") < 4 * 60 * 1000l) {
                logger.info("Trust login cookies as they're not that old");
                return;
            }
            br.getPage("https://www." + account.getHoster() + "/en/");
            if (br.containsHTML("/account/logout/")) {
                /* Cookie login successful */
                logger.info("Cookie login successful");
                return;
            }
            /* Full login required */
            logger.info("Cookie login failed");
            br.clearCookies(br.getHost());
        }
        logger.info("Performing full login");
        br.getPage("https://www." + account.getHoster() + "/en/account/login/");
        final Form loginform = br.getFormbyProperty("name", "FormLogin_form");
        loginform.put("email", account.getUser());
        loginform.put("password", account.getPass());
        loginform.put("remember", "1");
        br.submitForm(loginform);
        if (br.getCookie(br.getHost(), "authautologin") == null) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
        }
        account.saveCookies(br.getCookies(account.getHoster()), "");
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    /** no override to keep plugin compatible to old stable */
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        handleDL(link, account);
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        account.setConcurrentUsePossible(true);
        account.setMaxSimultanDownloads(-1);
        prepBr();
        br.setAllowedResponseCodes(new int[] { 400 });
        br.getPage(API_BASE + "/accountinfo?token=" + getToken(account));
        if (br.containsHTML("\"last_login\":null")) {
            account.setProperty("token", Property.NULL);
            br.getPage(API_BASE + "/accountinfo?token=" + getToken(account));
        }
        String trafficLeftMB = PluginJSonUtils.getJson(br, "credit");
        if (!StringUtils.isEmpty(trafficLeftMB)) {
            if (trafficLeftMB.matches("\\d+")) {
                trafficLeftMB += "MB";
            }
            ai.setTrafficLeft(SizeFormatter.getSize(trafficLeftMB));
        }
        br.getPage(API_BASE + "/list");
        String hostsSup = br.getRegex("\"list\":\"(.*?)\"").getMatch(0);
        if (hostsSup != null) {
            hostsSup = hostsSup.replace("\\", "");
            hostsSup = hostsSup.replaceAll("https?://(www\\.)?", "");
            final String[] hosts = hostsSup.split(";");
            final ArrayList<String> supportedHosts = new ArrayList<String>(Arrays.asList(hosts));
            ai.setMultiHostSupport(this, supportedHosts);
        }
        ai.setStatus("Premium account");
        account.setType(AccountType.PREMIUM);
        return ai;
    }

    private String getToken(final Account account) throws Exception {
        String token = account.getStringProperty("token", null);
        if (token == null) {
            br.getPage(API_BASE + "/login?email=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
            if (br.containsHTML("\"error\":\"login\\-failed\"")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
            token = PluginJSonUtils.getJson(br, "token");
            if (StringUtils.isEmpty(token)) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            account.setProperty("token", token);
        }
        return token;
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
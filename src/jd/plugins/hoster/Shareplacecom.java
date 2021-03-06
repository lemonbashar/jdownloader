//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.UserAgents;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "shareplace.com" }, urls = { "https?://[\\w\\.]*?shareplace\\.(?:com|org)/\\?(?:d=)?([\\w]+)(/.*?)?" })
public class Shareplacecom extends PluginForHost {
    public Shareplacecom(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        // they are switching to .org as main domain
        link.setUrlDownload(link.getDownloadURL().replaceFirst("\\.com", ".org"));
        link.setUrlDownload(link.getDownloadURL().replaceFirst("Download", ""));
    }

    @Override
    public String getAGBLink() {
        return "http://shareplace.com/rules.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 10;
    }

    private Browser prepBR(final Browser br) {
        br.getHeaders().put("User-Agent", UserAgents.stringUserAgent());
        br.setCustomCharset("UTF-8");
        br.setFollowRedirects(true);
        return br;
    }

    private static final String html_captcha = "/captcha\\.php";
    private String              correctedBR  = null;

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
        return new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        String url = link.getDownloadURL();
        if (StringUtils.containsIgnoreCase(url, ".com/")) {
            convert(link);
            url = link.getDownloadURL();
        }
        setBrowserExclusive();
        prepBR(this.br);
        br.setFollowRedirects(true);
        getPage(url);
        if (!this.br.getURL().contains(this.getFID(link))) {
            /* E.g. redirect to mainpage or errorpage. */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String iframe = br.getRegex("<frame name=\"main\" src=\"(.*?)\">").getMatch(0);
        if (iframe != null) {
            br.getPage(iframe);
        }
        if (new Regex(correctedBR, "Your requested file is not found").matches() || !br.containsHTML("Filename:<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = new Regex(correctedBR, "Filename:</font></b>(.*?)<b><br>").getMatch(0);
        String filesize = br.getRegex("Filesize.*?b>(.*?)<b>").getMatch(0);
        if (inValidate(filename)) {
            filename = br.getRegex("<title>(.*?)</title>").getMatch(0);
        }
        if (filesize == null) {
            filesize = br.getRegex("File.*?size.*?:.*?</b>(.*?)<b><br>").getMatch(0);
        }
        if (!inValidate(filename)) {
            /* Let's check if we can trust the results ... */
            filename = Encoding.htmlDecode(filename.trim());
            link.setFinalFileName(filename);
        } else if (!link.isNameSet()) {
            link.setName(this.getFID(link));
        }
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize.trim()));
        }
        return AvailableStatus.TRUE;
    }

    private boolean inValidate(final String s) {
        if (s == null || s.matches("\\s+") || s.equals("") || s.toLowerCase().contains("jdownloader")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        doFree(link);
    }

    private void doFree(final DownloadLink link) throws Exception {
        String dllink = null;
        final boolean checkDirecturlCandidates = true;
        /* 2016-08-23: Added captcha implementation */
        if (this.br.containsHTML(html_captcha)) {
            final String code = this.getCaptchaCode("mhfstandard", "/captcha.php?rand=" + System.currentTimeMillis(), link);
            this.br.postPage(this.br.getURL(), "captchacode=" + Encoding.urlEncode(code));
            if (this.br.containsHTML(html_captcha)) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            /* 2020-12-17: Pre-download wiattime can be skipped */
            // this.sleep(15 * 1001l, link);
        }
        for (final String[] s : br.getRegex("<script language=\"Javascript\">(.*?)</script>").getMatches()) {
            if (!new Regex(s[0], "(vvvvvvvvv|teletubbies|zzipitime)").matches()) {
                continue;
            }
            dllink = rhino(link, s[0], checkDirecturlCandidates);
            if (dllink != null) {
                break;
            }
        }
        if (dllink == null) {
            if (br.containsHTML("<span>You have got max allowed download sessions from the same IP\\!</span>")) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Limit reached or IP already loading", 60 * 60 * 1001l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!checkDirecturlCandidates) {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink);
            if (!this.looksLikeDownloadableContent(dl.getConnection())) {
                logger.warning("dllink doesn't seem to be a file...");
                try {
                    br.followConnection(true);
                } catch (final IOException e) {
                    logger.log(e);
                }
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown download error", 15 * 60 * 1000l);
            }
        }
        /* Workaround für fehlerhaften Filename Header */
        final String name = Plugin.getFileNameFromHeader(dl.getConnection());
        if (name != null) {
            link.setFinalFileName(Encoding.deepHtmlDecode(name));
        }
        dl.startDownload();
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink link, final PluginForHost plugin) {
        return link.getHost().equalsIgnoreCase(plugin.getHost());
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }

    private String rhino(final DownloadLink link, final String s, final boolean checkResult) {
        final String cleanup = new Regex(s, "(var.*?)var zzipitime").getMatch(0);
        final String[] vars = new Regex(s, "<a href=\"[a-z0-9 \\+]*'\\s*\\+\\s*(.*?)\\s*\\+\\s*'\"").getColumn(0);
        if (vars != null) {
            final ArrayList<String> vrrs = new ArrayList<String>(Arrays.asList(vars));
            // Collections.reverse(vrrs);
            for (final String var : vrrs) {
                String result = null;
                final ScriptEngineManager manager = JavaScriptEngineFactory.getScriptEngineManager(this);
                final ScriptEngine engine = manager.getEngineByName("javascript");
                try {
                    engine.eval(cleanup);
                    result = (String) engine.get(var);
                } catch (final Throwable e) {
                    continue;
                }
                // crap here
                final String crap = new Regex(s, "<a href=\"([a-z]+)'\\s*\\+\\s*" + var).getMatch(0);
                if (crap != null) {
                    result = crap + result;
                }
                try {
                    new URL(result);
                } catch (Exception e) {
                    logger.log(e);
                    continue;
                }
                if (result == null || (result.contains("jdownloader") && !result.startsWith("http"))) {
                    continue;
                }
                if (checkResult) {
                    try {
                        dl = jd.plugins.BrowserAdapter.openDownload(br, link, result);
                        if (this.looksLikeDownloadableContent(dl.getConnection())) {
                            return result;
                        } else {
                            logger.info("Skipping potential final downloadurl: " + result);
                            try {
                                dl.getConnection().disconnect();
                            } catch (final Throwable t) {
                            }
                            continue;
                        }
                    } catch (final Exception e) {
                        logger.log(e);
                    }
                } else {
                    return result;
                }
            }
        }
        return null;
    }

    private void getPage(final String url) throws IOException, NumberFormatException, PluginException {
        br.getPage(url);
        correctBR();
    }

    /* Removes HTML code which could break the plugin */
    private void correctBR() throws NumberFormatException, PluginException {
        correctedBR = br.toString();
        ArrayList<String> regexStuff = new ArrayList<String>();
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        /* generic cleanup */
        regexStuff.add("<\\!(\\-\\-.*?\\-\\-)>");
        regexStuff.add("(display: ?none;\">.*?</div>)");
        regexStuff.add("(visibility:hidden>.*?<)");
        for (String aRegex : regexStuff) {
            String results[] = new Regex(correctedBR, aRegex).getColumn(0);
            if (results != null) {
                for (String result : results) {
                    correctedBR = correctedBR.replace(result, "");
                }
            }
        }
    }
}
/*
 * Copyright (C) 2013 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.jenkins.plugins;

import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.io.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.Part;

import brut.androlib.AndrolibException;
import brut.androlib.res.AndrolibResources;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.value.ResStringValue;
import brut.androlib.res.data.value.ResValue;
import brut.androlib.res.util.ExtFile;

class DeployStrategyAndroid extends DeployStrategy {

    private final FilePath mApkFile;

    public DeployStrategyAndroid(final String server, final String id, final String tag,
                                 final boolean prependNameToTag, final FilePath apkFile, final AbstractBuild build,
                                 final BuildListener listener) {
        super(server, "android", id, tag, prependNameToTag, build, listener);
        mApkFile = apkFile;
    }

    @Override
    public List<Part> getParts() throws FileNotFoundException {
        List<Part> parts = super.getParts();
        parts.add(new FilePart("apk", new FilePathPartSource(mApkFile)));
        return parts;
    }

    @Override
    public String getVersion() {
        String version = getStringFromManifest("versionName");
        if (version != null) {
            return version;
        }
        return super.getVersion();
    }

    @Override
    public String getDeployableName() {
        return mApkFile.getName().replace(".apk", "");
    }

    private String getStringFromManifest(final String name) {
        File tempApk = null;
        InputStream is = null;
        ZipFile zip = null;
        try {
            tempApk = File.createTempFile(getBuild().getId(), "nr-" + getBuild().getNumber());
            mApkFile.copyTo(new FileOutputStream(tempApk));
            zip = new ZipFile(tempApk);
            ZipEntry mft = zip.getEntry("AndroidManifest.xml");
            is = zip.getInputStream(mft);

            byte[] xml = new byte[is.available()];
            is.read(xml);

            String string = AndroidUtils.decompressXML(xml);
            int start = string.indexOf(name + "=\"") + name.length() + 2;
            int end = string.indexOf("\"", start);
            String version = string.substring(start, end);

            if (version.startsWith("resourceID 0x")) {
                int resId = Integer.parseInt(version.substring(13), 16);
                return getStringFromResource(tempApk, resId);
            } else {
                return version;
            }
        } catch (Exception e) {
            getLogger().println(TAG + "Error: " + e.getMessage());
        } finally {
            if (tempApk != null)
                tempApk.delete();
            if (zip != null)
                try {
                    zip.close();
                } catch (IOException e) {
                    getLogger().println(TAG + "Error: " + e.getMessage());
                }
            if (is != null)
                try {
                    is.close();
                } catch (IOException e) {
                    getLogger().println(TAG + "Error: " + e.getMessage());
                }
        }
        return null;
    }

    private String getStringFromResource(final File tempApk, final int resId) throws AndrolibException {
        AndrolibResources res = new AndrolibResources();
        ExtFile file = new ExtFile(tempApk);
        ResTable table = res.getResTable(file);
        ResPackage defaultPackage = table.listMainPackages().iterator().next();

        ResValue value = table.getValue(defaultPackage.getName(),
                "string", table.getResSpec(resId).getName());
        return ((ResStringValue) value).encodeAsResXmlValue();
    }
}

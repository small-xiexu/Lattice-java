package com.xbk.lattice.api.admin;

/**
 * 资料源创建请求。
 *
 * 职责：承载 Git / SERVER_DIR 资料源创建所需的配置参数
 *
 * @author xiexu
 */
public class AdminSourceCreateRequest {

    private String sourceCode;

    private String name;

    private String contentProfile;

    private String visibility;

    private String defaultSyncMode;

    private String remoteUrl;

    private String branch;

    private String credentialRef;

    private String serverDir;

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentProfile() {
        return contentProfile;
    }

    public void setContentProfile(String contentProfile) {
        this.contentProfile = contentProfile;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getDefaultSyncMode() {
        return defaultSyncMode;
    }

    public void setDefaultSyncMode(String defaultSyncMode) {
        this.defaultSyncMode = defaultSyncMode;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public String getServerDir() {
        return serverDir;
    }

    public void setServerDir(String serverDir) {
        this.serverDir = serverDir;
    }
}

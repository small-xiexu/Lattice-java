package com.xbk.lattice.api.admin;

import com.xbk.lattice.source.domain.SourceCredential;
import com.xbk.lattice.source.service.SourceCredentialService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 资料源凭据后台控制器。
 *
 * 职责：暴露资料源凭据列表与保存接口
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/source-credentials")
public class AdminSourceCredentialController {

    private final SourceCredentialService sourceCredentialService;

    /**
     * 创建资料源凭据后台控制器。
     *
     * @param sourceCredentialService 资料源凭据服务
     */
    public AdminSourceCredentialController(SourceCredentialService sourceCredentialService) {
        this.sourceCredentialService = sourceCredentialService;
    }

    /**
     * 查询全部资料源凭据。
     *
     * @return 脱敏后的凭据列表
     */
    @GetMapping
    public List<AdminSourceCredentialResponse> listCredentials() {
        List<AdminSourceCredentialResponse> responses = new ArrayList<AdminSourceCredentialResponse>();
        for (SourceCredential sourceCredential : sourceCredentialService.listCredentials()) {
            responses.add(toResponse(sourceCredential));
        }
        return responses;
    }

    /**
     * 保存资料源凭据。
     *
     * @param request 请求
     * @return 脱敏后的凭据
     */
    @PostMapping
    public AdminSourceCredentialResponse saveCredential(@RequestBody AdminSourceCredentialRequest request) {
        SourceCredential sourceCredential = sourceCredentialService.save(
                request.getCredentialCode(),
                request.getCredentialType(),
                request.getSecret(),
                request.getUpdatedBy()
        );
        return toResponse(sourceCredential);
    }

    private AdminSourceCredentialResponse toResponse(SourceCredential sourceCredential) {
        return new AdminSourceCredentialResponse(
                sourceCredential.getId(),
                sourceCredential.getCredentialCode(),
                sourceCredential.getCredentialType(),
                sourceCredential.getSecretMask(),
                sourceCredential.isEnabled(),
                sourceCredential.getUpdatedAt() == null ? null : sourceCredential.getUpdatedAt().toString()
        );
    }
}

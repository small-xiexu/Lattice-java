import type {
  DocumentParseConnection,
  DocumentParsePolicy,
  DocumentParseProvider,
} from "../api/contracts/document-parse-settings";

export function documentParseProviderFixture(
  overrides: Partial<DocumentParseProvider> = {},
): DocumentParseProvider {
  return {
    providerType: "tencent_ocr",
    displayName: "腾讯 OCR",
    defaultBaseUrl: "",
    probeMode: "json_body_sync",
    supportedCapabilities: ["IMAGE_OCR", "SCANNED_PDF_OCR"],
    credentialFields: [
      {
        fieldKey: "secretId",
        label: "Secret ID",
        inputType: "text",
        required: true,
        defaultValue: "",
        placeholder: "请输入 Secret ID",
        description: "腾讯 OCR Secret ID",
      },
      {
        fieldKey: "secretKey",
        label: "Secret Key",
        inputType: "password",
        required: true,
        defaultValue: "",
        placeholder: "请输入 Secret Key",
        description: "腾讯 OCR Secret Key",
      },
    ],
    configFields: [
      {
        fieldKey: "endpointPath",
        label: "接口路径",
        inputType: "text",
        required: true,
        defaultValue: "/ocr/v1/general-basic",
        placeholder: "例如 /ocr/v1/general-basic",
        description: "JSON Body OCR 接口路径",
      },
    ],
    ...overrides,
  };
}

export function textInProviderFixture(
  overrides: Partial<DocumentParseProvider> = {},
): DocumentParseProvider {
  return documentParseProviderFixture({
    providerType: "textin_xparse",
    displayName: "TextIn xParse",
    defaultBaseUrl: "https://api.textin.com",
    probeMode: "textin_multipart_sync",
    credentialFields: [
      {
        fieldKey: "appId",
        label: "App ID",
        inputType: "text",
        required: true,
        defaultValue: "",
        placeholder: "请输入 App ID",
        description: "TextIn xParse App ID",
      },
      {
        fieldKey: "secretCode",
        label: "Secret Code",
        inputType: "password",
        required: true,
        defaultValue: "",
        placeholder: "请输入 Secret Code",
        description: "TextIn xParse Secret Code",
      },
    ],
    configFields: [
      {
        fieldKey: "endpointPath",
        label: "接口路径",
        inputType: "text",
        required: true,
        defaultValue: "/api/v1/xparse/parse/sync",
        placeholder: "例如 /api/v1/xparse/parse/sync",
        description: "TextIn 同步解析接口路径",
      },
      {
        fieldKey: "parseConfigJson",
        label: "解析配置 JSON",
        inputType: "textarea",
        required: false,
        defaultValue: "{}",
        placeholder: "例如 {\"parse_mode\":\"scan\"}",
        description: "提交给 TextIn 的额外解析配置",
      },
    ],
    ...overrides,
  });
}

export function documentParseConnectionFixture(
  overrides: Partial<DocumentParseConnection> = {},
): DocumentParseConnection {
  return {
    id: 7,
    connectionCode: "tencent-ocr-main",
    providerType: "tencent_ocr",
    baseUrl: "https://ocr.example.test",
    credentialMask: "secretId=doc-****3456, secretKey=doc-****4321",
    credentialConfigured: true,
    configJson: "{\"endpointPath\":\"/ocr/v1/general-basic\"}",
    enabled: true,
    createdBy: "admin",
    updatedBy: "admin",
    createdAt: "2026-07-22T10:00:00Z",
    updatedAt: "2026-07-22T10:00:00Z",
    ...overrides,
  };
}

export function documentParsePolicyFixture(
  overrides: Partial<DocumentParsePolicy> = {},
): DocumentParsePolicy {
  return {
    id: 3,
    policyScope: "default",
    imageConnectionId: 7,
    scannedPdfConnectionId: 7,
    cleanupEnabled: false,
    cleanupModelProfileId: null,
    fallbackPolicyJson: "{}",
    createdBy: "admin",
    updatedBy: "admin",
    createdAt: "2026-07-22T10:00:00Z",
    updatedAt: "2026-07-22T10:00:00Z",
    ...overrides,
  };
}

# Security Policy

Lattice compiles untrusted files and Git repositories into a knowledge base and exposes HTTP, CLI, MCP, model-provider, Vault, snapshot, and rollback operations. Security reports are welcome in English or Chinese.

## Supported versions

This project currently has no tagged release. Security fixes are made on the latest `main` branch only; older commits are not supported.

## Reporting a vulnerability

Do not publish exploit details, credentials, private data, or a working proof of concept in a public issue.

1. Prefer GitHub's **Security > Advisories > Report a vulnerability** flow when it is available.
2. If private reporting is unavailable, open a public issue titled `Security contact request` and mention `@small-xiexu`. Include no vulnerability details. The maintainer will arrange a private channel.
3. In the private report, include the affected commit, impact, prerequisites, a minimal reproduction using fake credentials and temporary data, and any proposed mitigation.

There is no guaranteed response SLA. The maintainer will acknowledge and triage reports as capacity permits, coordinate remediation, and agree on disclosure timing before technical details are published.

## In-scope risks

- Authentication or authorization bypass affecting admin APIs, MCP tools, model configuration, or destructive maintenance actions.
- Path traversal, unsafe symbolic-link handling, or unintended reads, writes, deletes, exports, snapshots, or rollbacks outside an approved root.
- SSRF, unsafe Git transports, redirects to private networks, or other unauthorized outbound requests.
- Prompt injection from documents, repositories, metadata, or schema files that changes model or tool behavior.
- API keys, credentials, prompts, source content, or secrets exposed through configuration, logs, errors, browser storage, or generated artifacts.
- Malicious Maven/npm dependencies, GitHub Actions, build scripts, or third-party contributions.
- Denial of service or integrity failures in ingestion, retrieval, indexing, and model-call workflows.

## Safe testing rules

- Test only systems and data you own or are authorized to use.
- Use fake credentials, local services, temporary directories, and non-sensitive fixtures.
- Do not call paid or third-party model providers without explicit authorization.
- Do not access other users' data, scan unrelated hosts, disrupt services, or run destructive tests against shared or production environments.
- Stop testing once sufficient evidence exists and include cleanup steps in the report.

## Deployment note

The repository is under active development and does not currently claim a hardened multi-tenant security boundary. Operators should use unique secrets, restrict admin and MCP endpoints to trusted networks, allow only intended source locations and remote hosts, and review imported content and third-party changes before execution.

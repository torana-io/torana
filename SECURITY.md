# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.x.x   | :white_check_mark: |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue in Torana, please report it responsibly.

### How to Report

1. **Do NOT create a public GitHub issue** for security vulnerabilities
2. Email your findings to the maintainers (see CONTRIBUTING.md for contact info)
3. Include as much detail as possible:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

### What to Expect

- **Acknowledgment**: We will acknowledge receipt within 48 hours
- **Assessment**: We will assess the vulnerability and determine its severity
- **Fix Timeline**: Critical vulnerabilities will be addressed as quickly as possible
- **Disclosure**: We will coordinate with you on public disclosure timing

### Security Best Practices

When using Torana:

1. **Redaction**: Always configure appropriate redaction policies for sensitive data
2. **Database Security**: Ensure your audit database has proper access controls
3. **Transport Security**: Use TLS for database connections
4. **Access Control**: Limit who can query audit records based on your organization's needs

## Security Features

Torana includes several security-focused features:

- **Redaction Policy**: Built-in redaction for sensitive fields (passwords, tokens, SSN, etc.)
- **Append-Only Writes**: Audit records are immutable once written
- **Structured Data**: Prevents injection through structured, typed data model

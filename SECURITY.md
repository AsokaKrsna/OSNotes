# Security Policy

## 🔒 Our Commitment

OSNotes takes security seriously. We believe in transparency and responsible disclosure to keep our users safe.

## 🛡️ Security Features

### Privacy by Design
- **No internet permission** - Your notes never leave your device
- **No tracking or analytics** - We don't collect any user data
- **No account required** - No personal information stored
- **Local storage only** - All data stays on your device

### Data Protection
- **Standard PDF format** - No proprietary encryption that could lock you out
- **File-based storage** - You control where your notes are saved
- **No cloud sync** - No risk of data breaches from cloud storage
- **Open source** - Security through transparency

## 🐛 Reporting a Vulnerability

Found a security issue? We appreciate your help in keeping OSNotes secure.

### What to Report
- Security vulnerabilities in the app
- Data leakage or privacy concerns
- Authentication/authorization issues
- Injection vulnerabilities
- Cryptographic weaknesses

### How to Report

**Please DO NOT open a public issue for security vulnerabilities.**

Instead, report privately:

1. **Email**: security@osnotes.app (or your email)
2. **Subject**: `[SECURITY] Brief description`
3. **Include**:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)
   - Your contact information

### What to Expect

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 1 week
- **Status updates**: Every 2 weeks
- **Fix timeline**: Depends on severity
  - Critical: 1-7 days
  - High: 1-4 weeks
  - Medium: 1-3 months
  - Low: Best effort

### Responsible Disclosure

We follow responsible disclosure practices:

1. **Report received** - We acknowledge your report
2. **Investigation** - We verify and assess the issue
3. **Fix developed** - We create and test a patch
4. **Release** - We release the fix to users
5. **Public disclosure** - After users have had time to update (typically 30 days)

### Recognition

We believe in giving credit where it's due:

- Security researchers will be acknowledged in release notes (if desired)
- We maintain a [Security Hall of Fame](SECURITY_HALL_OF_FAME.md)
- Serious vulnerabilities may be eligible for recognition (no bounty program yet)

## 🔐 Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | ✅ Yes             |
| < 1.0   | ❌ No              |

We support the latest stable release. Please update to the latest version for security fixes.

## 🛠️ Security Best Practices

### For Users

**Keep Your Device Secure:**
- Use device encryption
- Set a strong lock screen password
- Keep Android updated
- Install apps only from trusted sources

**Protect Your Notes:**
- Use device backup features
- Store sensitive notes in encrypted folders
- Be cautious when sharing PDFs
- Review app permissions regularly

### For Developers

**Code Security:**
- Follow secure coding practices
- Validate all user inputs
- Use parameterized queries
- Avoid hardcoded secrets
- Keep dependencies updated

**Testing:**
- Run security scans regularly
- Test for common vulnerabilities (OWASP Mobile Top 10)
- Review code for security issues
- Test on multiple Android versions

## 📋 Known Limitations

### Current Scope
OSNotes is designed for **local, personal note-taking**. It is NOT designed for:
- Multi-user environments
- Highly sensitive/classified information
- Regulatory compliance (HIPAA, GDPR data processing, etc.)
- Enterprise security requirements

### Technical Limitations
- **No encryption at rest** - Notes are stored as standard PDFs
- **No password protection** - Relies on device security
- **No secure deletion** - Standard file deletion (not DoD-level wiping)
- **No audit logging** - No tracking of who accessed what

**Recommendation**: For highly sensitive information, use additional encryption tools or specialized secure note apps.

## 🔍 Security Audits

### Self-Audits
- Regular code reviews
- Dependency vulnerability scans
- Static analysis tools
- Manual security testing

### External Audits
- No formal security audit yet
- Open to community security reviews
- Welcoming security researchers

## 📚 Security Resources

### Android Security
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android Security Bulletin](https://source.android.com/security/bulletin)

### Secure Development
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [Kotlin Security](https://kotlinlang.org/docs/security.html)

## 📞 Contact

**Security Team**: security@osnotes.app (or your email)

**PGP Key**: Available on request

**Response Time**: 
- Critical issues: 24-48 hours
- Other issues: 1 week

## 🙏 Thank You

Thank you for helping keep OSNotes and its users safe. Responsible security researchers make the open-source community stronger.

---

**Last Updated**: December 2024

**Version**: 1.0

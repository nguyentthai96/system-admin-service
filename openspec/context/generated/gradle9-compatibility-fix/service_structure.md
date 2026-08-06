# Service Structure

_Generated: 2026-08-06_

## system-admin-service

### Detected Packages

- `com.ntt.sysadmin.apipartner`: API partner / key management module (1 file: `ApiKeyService.kt`)
- `com.ntt.sysadmin.menu`: Menu management module (1 file: `MenuController.kt`)
- `com.ntt.systemadminservice`: Application root — main entry point (`SystemAdminServiceApplication.kt`)
- `com.ntt.systemadminservice.shared.session`: Shared session infrastructure (`DefaultSessionManagement.kt`)

### Not Found

- `controller/` (MenuController is in `menu/` — feature-based, not layer-based)
- `handler/`
- `factory/`
- `model/`
- `entity/`
- `repository/`
- `dto/`
- `config/`

### Naming Convention

- Package: Feature-based (`apipartner`, `menu`) + shared (`shared/session`)
- Classes: PascalCase, suffix-based (`*Service`, `*Controller`, `Default*`)
- 2 root packages detected: `com.ntt.sysadmin` (feature code) + `com.ntt.systemadminservice` (infra/app)
- Language: Kotlin (100%)

### Notes

- Service đang ở giai đoạn rất sớm — chỉ 4 source files
- Dual root package (`sysadmin` vs `systemadminservice`) — có thể cần normalize sau
- Không có layer separation (controller/service/repository) — code nằm flat trong feature packages

# ClickMóvil

Tienda virtual Android de accesorios para celular (Fundación Universitaria Compensar).
Actividad 6 — Aplicativo funcional con Firebase Auth, Firestore, Storage, geolocalización y checkout.

## Requisitos

- Android Studio Ladybug o superior
- JDK 17
- Emulador o dispositivo con Android 8.0 (API 26) o superior

## Cómo abrirlo

1. Abre la carpeta `ClickMovil` en Android Studio.
2. Espera la sincronización de Gradle.
3. Ejecuta la app (`Run`) en emulador o celular.

## Cuentas de prueba

| Correo | Contraseña | Rol |
|---|---|---|
| ana@correo.com | 123456 | Comprador |
| vendedor@correo.com | 123456 | Vendedor |
| admin@correo.com | admin123 | Administrador |

## Pantallas por rol

### Transversal
Splash, login, registro, recuperación de contraseña, autenticación biométrica y `BottomNavigationView` por rol.

### Comprador
Inicio con fotos de catálogo, catálogo, detalle de producto, carrito, ubicación de entrega, checkout con referencia EPAYCO, pedidos y perfil.

### Vendedor
CRUD de productos (con cámara/galería), historial de pedidos recibidos y perfil.

### Administrador
Panel con totales, accesos y pedidos recientes; gestión de usuarios (crear/editar/activar/eliminar), productos y reporte de ventas.

## Estructura del proyecto

```
app/src/main/
├── java/.../clickmovil/
│   ├── data/          # Firestore, Storage, sesión y esquema SQLite anterior
│   ├── model/         # Entidades y roles
│   ├── ui/            # Activities y Fragments por rol
│   └── util/          # Navegación y formato
└── res/
    ├── layout/        # XML de pantallas e ítems
    ├── menu/          # Navegación inferior por rol
    ├── drawable/      # Vectores, fondos y chips
    ├── color/         # Selectores (nav)
    ├── values/        # colors, dimens, strings, themes
    └── xml/           # FileProvider
```

## Recursos de interfaz (no usa tema por defecto)

- `res/values/colors.xml` — paleta navy/teal propia
- `res/values/strings.xml` — textos centralizados
- `res/values/dimens.xml` — espacios, radios y tipografías
- `res/values/themes.xml` — `Theme.ClickMovil` y estilos de botón/input
- `res/drawable/` — logo, íconos y fondos personalizados

## Generar APK de validación

En Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
El APK queda en `app/build/outputs/apk/debug/`.

## Repositorio

https://github.com/MancipeManuel/ClickMovil

## Commits

El historial refleja avance incremental: proyecto base → splash/login → flujo comprador → paneles vendedor/admin → pulido de UI y recursos.

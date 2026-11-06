# ClickMóvil

Tienda virtual Android de accesorios para celular. Proyecto de la actividad de interfaces gráficas (comprador, vendedor y administrador).

## Cómo abrirlo

1. Abre la carpeta `ClickMovil` en Android Studio.
2. Espera la sincronización de Gradle.
3. Ejecuta la app en un emulador o en un celular con Android 8 o superior.

## Cuentas de prueba

| Correo | Contraseña | Rol |
|---|---|---|
| ana@correo.com | 123456 | Comprador |
| vendedor@correo.com | 123456 | Vendedor |
| admin@correo.com | admin123 | Administrador |

## Pantallas por rol

- Transversal: splash, inicio de sesión, biometría, recuperación de contraseña, registro y barra inferior.
- Comprador: inicio, catálogo, detalle, carrito, pasarela de pago, pedidos y perfil.
- Vendedor: productos (crear, editar, retirar), pedidos recibidos y perfil.
- Administrador: panel, usuarios (crear, editar, desactivar, eliminar), productos y reporte de ventas.

## Recursos de interfaz

- `res/values/colors.xml`: paleta azul oscuro y verde azulado, distinta del tema por defecto.
- `res/values/strings.xml`: textos de la interfaz.
- `res/values/dimens.xml`: espacios, radios y tamaños.
- `res/values/themes.xml`: tema, botones y estilos de texto.
- `res/drawable`: ícono, logo y vectores del menú.
- `res/layout`: activities y fragments por pantalla.
- `res/menu`: navegación inferior de cada rol.

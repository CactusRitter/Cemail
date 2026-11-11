# Diseño de Cemail (familia "Ce")

Cemail forma parte de las apps "Ce" que César y Jonathan hacen juntos: **Celander** (calendario), **Cenotes** (notas) y **Cemail**. Las tres comparten el mismo lenguaje visual para que parezcan una sola familia.

## Fuentes de referencia
- **Celander 1.07** (no la 1.08, que aún necesita cambios): https://celandar.winterwonderland.cloud/ — el changelog está en `/changelog/`.
- **Cenotes**: `D:\cesar\Documents\Claude Projects\Cenotes` — ya implementa el diseño de Celander en Compose. Cemail copia de ahí el tema y los componentes de cristal (`ui/theme/Theme.kt`, `ui/glass/Glass.kt`) con los mismos valores.
- Cuando Jonathan dé acceso al repositorio de Celander, se contrastan estos valores con el código real y se ajusta lo que difiera.

## Principios (de Celander)
1. **Cristal esmerilado** ("Frosted Glass" en Android): superficies translúcidas con desenfoque, borde claro que se desvanece hacia abajo y sombra suave.
2. **Resortes, no curvas**: todos los cambios de estado usan `glassSpring()` (amortiguación 0.8, rigidez media-baja). Nada de animaciones lineales.
3. **Navegación flotante**: píldora de cristal (`GlassPill` + `PillIcon`) y botones redondos de 56 dp (`GlassIconButton`) sobre el contenido.
4. **Claro y oscuro** siguiendo el sistema, ambos con la misma importancia.
5. **Accesibilidad**: contraste WCAG AA, objetivos táctiles grandes, texto escalable hasta 200 %, opción de reducir animaciones.
6. **Transparencia ajustable** (0–100 %, 65 % por defecto), igual que en Celander y Cenotes.

## Paleta
| Uso | Claro | Oscuro |
|---|---|---|
| Fondo (`background`) | `#ECE6F6` | `#141B33` |
| Superficie | `#F7F5FA` | `#1C2033` |
| Texto | `#1B1B21` | `#F2F1F7` |
| Texto secundario | `#5D5B66` | `#B5B3C2` |
| Error | `#C62828` | `#FF8A80` |
| Cristal base (tras el desenfoque) | `#F1ECF4` | `#171B2E` |
| Tarjeta / píldora (tinte) | blanco | `#262C45` |
| Menús y diálogos | `#F9F7FC` al 97 % | `#262B40` al 97 % |

Fondo: degradado vertical con manchas de color difuminadas (`GlassBackground`).

**Acentos** (cada uno afinado para claro/oscuro; por defecto Índigo): Índigo `#4A4FD8`/`#A9ADFF`, Violeta `#7B4FD6`/`#C7AEFF`, Azul `#1F6FD1`/`#9CC7FF`, Verde `#2E8B57`/`#8FD9AE`, Coral `#D9534F`/`#FFB0A8`, Ámbar `#B7791F`/`#F5C77E`.

**Marca de Celander** (web): degradado azul `#2A72FF` → índigo `#6168F1` → violeta `#8561EA` → lila `#AB64F0` → malva `#D57ABD` → rosa `#F08B94` → melocotón `#FC9774`. El icono de las apps usa un trazo con degradado índigo → violeta → coral y un punto coral.

## Tipografía
La web de Celander usa Inter (texto), Instrument Serif (titulares) y JetBrains Mono (detalles). Cenotes usa la tipografía del sistema (Roboto) en la app; Cemail hace lo mismo hasta que se decida otra cosa junto con Celander.

## Cómo se usa en el código
- `CemailTheme(settings)` aplica el esquema de colores y proporciona `LocalGlass`.
- `Modifier.glass(shape, kind, elevation, hazeState)` crea cualquier superficie de cristal. Para desenfocar lo que hay detrás, el contenido que se desplaza lleva `hazeSource` y la superficie recibe el mismo `HazeState`.
- `GlassKind`: `Card` (filas y tarjetas), `Chrome` (botones y píldoras), `Pinned` (resaltado con acento, p. ej. correo sin leer), `Menu` (diálogos y menús, casi opacos).
- Los ajustes de acento y transparencia viven en `SettingsRepository` (SharedPreferences, como Cenotes).
- Ningún color ni texto fijo en las pantallas: se toman del tema (`MaterialTheme.colorScheme`, `LocalGlass`) y de `strings.xml`.

## Cómo se vería en un correo (guía para las pantallas)
- **Bandeja**: filas como tarjetas de cristal; sin leer = `GlassKind.Pinned`. Botón flotante redondo para redactar y píldora con carpetas.
- **Lectura**: el cuerpo en una tarjeta de cristal; acciones en una píldora flotante inferior.
- **Redactar**: campos sobre tarjeta de cristal; el botón de enviar con el color de acento.

## Movimiento (añadido en Cemail 0.31.0; falta llevarlo a Celander y Cenotes)

Todo se mueve con **resortes**, nunca con curvas lineales (ver la regla 2). Hay tres, en `ui/glass/Motion.kt` y `Glass.kt`:

| Resorte | Amortiguación | Para qué |
|---|---|---|
| `glassSpring()` | 0,8 · rigidez media-baja | cambios de color y de tamaño; entradas y salidas suaves (fundidos) |
| `bounceSpring()` | 0,55 · rigidez media | lo que se pulsa o «salta»: botones, iconos, ventanas pequeñas (rebote visible) |
| `softBounceSpring()` | 0,72 · rigidez media-baja | pantallas enteras y paneles grandes (rebote suave) |

Efectos reutilizables (siempre los mismos, no se inventan otros):
- `pressScale()`: el elemento se encoge al pulsarlo y vuelve con rebote. Va **el primero** de la cadena de modificadores, antes de `glass` y `clickable`.
- `popIn(visible, delayMillis, initialScale, origin)`: aparece creciendo desde el centro (o desde el botón del que sale) hasta su tamaño, con rebote y fundido. Con `delayMillis` escalonado, los elementos de un grupo salen uno tras otro.
- `bumpOn(trigger)`: un «pop» cada vez que cambia algo (la estrella al marcarla).
- Pantallas: la nueva crece desde un poco más pequeña (0,9) con `softBounceSpring` y la anterior se desvanece; al volver atrás, al revés (profundidad).
- Listas: `animateItem` con `softBounceSpring` al reordenar y fundido al entrar y salir.
- Avisos (barra de deshacer): suben desde abajo con rebote y bajan con `glassSpring`.

Reglas: las ventanas y menús salen de su botón (`origin`); lo que está en el centro, del centro. Si Android tiene las animaciones desactivadas, Compose las salta solo.

## Ajustes y filas grandes (añadido en Cemail 0.32.0)
- **Fila grande** (`BigRow`): tarjeta de cristal `Card` con esquina de 28 dp, icono en un círculo (`primaryContainer`), título, una línea de explicación y flecha. Entra con `popIn` escalonado y se pulsa con `pressScale(0.97)`. Se usa para las secciones de Ajustes, las cuentas y los tipos de cuenta que se pueden añadir (ahí con más altura).
- **Ajustes** es un árbol corto de pantallas (principal → sección → detalle) con transición de profundidad; el botón atrás sube un nivel. Cada pantalla agrupa controles en `SettingsCard`; las opciones de varias posibilidades son `ChoiceChip` (píldora de cristal, la elegida `Pinned` y en negrita).
- **Logotipos de marca**: Google y Microsoft se dibujan en `BrandIcons.kt` con sus colores reales. Son los **únicos colores fijos** de la app y solo se usan en la pantalla de añadir cuenta.
- **Gestos de deslizar**: el fondo que aparece al arrastrar usa `primaryContainer`, o `errorContainer` para las acciones destructivas (papelera, spam). El umbral es una fracción del ancho (30 / 45 / 60 %).
- **Abrir un correo** (0.33.1): el visor crece desde la tarjeta tocada (`expandFrom`, rectángulo redondeado que se abre con muelle `dampingRatio 0,9 / stiffness 170` mientras el contenido aparece), y la lista se desvanece detrás. Al cerrar se hace lo opuesto: el visor se pliega hasta la tarjeta (su posición actual, `ReadTransition.positions`) y la lista aparece detrás (`targetContentZIndex = -1`). Sin origen (notificación, búsqueda) se usa la transición de pantallas de siempre.
- **Cabecera** (0.33.1): el título va en el centro de la pantalla (`CenteredHeader`), no en el hueco que dejan los extremos.

## Cuentas: foto, nombre y color (añadido en Cemail 1.0.0)
- **Color de cada cuenta**: se elige entre los colores de acento del sistema (`Accent`, el mismo juego que Celander usa para sus calendarios), con su tono claro u oscuro según el tema; «Automático» usa `accountPalette()` por posición. No hay colores libres.
- **Foto de cuenta** (`AccountAvatar`): la del proveedor (Google o Microsoft) o una de la galería, recortada en círculo; sin foto, la inicial sobre el contenedor del color de la cuenta. En listas de cuentas lleva un **aro de 2 dp** en el color de la cuenta.
- **Campo «De»** al redactar: misma tipografía que «Para» (etiqueta `bodyMedium`, dirección `labelLarge`); el desplegable lista las cuentas con su foto.
- **Barra de formato** al redactar: solo aparece mientras se escribe el texto del mensaje (entra con muelle `bounceSpring`), para no tapar las sugerencias de destinatarios.

## Color de acento y modo claro/oscuro (añadido en Cemail 1.3.0, igual que Celander)
- **Ocho colores**, los de Celander y en su orden: índigo `#4A54D6`, azul `#296DB5`, verde azulado `#1C7B77`, verde `#1D7F46`, ocre `#8F6721`, teja `#B54529`, frambuesa `#BD2B65`, violeta `#8B3AD2` (`Accent`, en `Theme.kt`). De cada uno salen, con el mismo tono, su versión para fondos oscuros (luminosidad 0,78) y los contenedores claro (0,93) y oscuro (0,30) (`paletteFrom`).
- **Color personalizado**: cualquier tono de una barra arcoíris. Amarillos y cianes se oscurecen un poco para que tengan la misma calma que los ocho (`customPalette`).
- El acento colorea botones, selecciones, el cristal destacado y la mancha de color del fondo.
- **Claro u oscuro**: Sistema / Claro / Oscuro. Los iconos de la barra de estado siguen la elección, no el teléfono.
- **Deslizador** (`GlassSlider`): pista redondeada de 12 dp (rellena con el acento hasta el botón, o pintada entera como la barra de tono) y botón de 28 dp con aro blanco y sombra suave, como Celander. Sustituye al `Slider` de Material.

## Interruptor y listas de carpetas (añadido en Cemail 1.1.0)
- **Interruptor** (`GlassSwitch`, en `ui/glass`): pista de 40 × 24 dp redondeada; encendido se llena con `primary` y el botón (`onPrimary`) salta al otro lado con `bounceSpring`; apagado, pista de cristal tenue (`surfaceVariant` translúcido) con borde `outline` y el botón algo más pequeño. Sustituye al `Switch` de Material en toda la app (más pequeño y con nuestro movimiento). La fila entera es la zona táctil.
- **Carpetas para sincronizar**: una sola tarjeta con una fila por carpeta (icono de carpeta, nombre, interruptor), como Samsung Email; las subetiquetas («Padre/Hijo») van sangradas 24 dp por nivel bajo su madre, y una madre que no es etiqueta sale como encabezado sin interruptor. La bandeja de entrada va primero, encendida y fija.
- **Abrir un correo con HTML**: nunca se enseña su texto plano antes; la tarjeta queda vacía hasta que la página está pintada y entonces aparece con un fundido (`glassSpring`).
- **Logotipo de Yahoo**: «Y!» blanco sobre su morado, en un cuadrado redondeado (`BrandIcons.kt`, solo en la pantalla de añadir cuenta).

## Estilos de fondo (prueba en la rama `temas`)
- **Estilo** (`ThemeStyle`, Ajustes → Apariencia): cambia el fondo y el tinte del cristal, aparte de claro/oscuro y del acento. Cada estilo tiene versión clara y oscura; el fondo (degradado + manchas de luz `Blob`) y los colores de página/superficie viven en `GlassColors` y los dibuja `GlassBackground`.
- **Clásico**: el lavanda de siempre. **Neutro**: gris niebla (claro) o grafito (oscuro), casi sin color; el acento solo aparece en botones y selecciones. **Aurora**: cielo profundo con luces grandes (acento, verde, violeta; en claro, acento, rosa, menta) detrás del cristal.
- **Atardecer** (coral y magenta), **Océano** (turquesa y azul) y **Neón** (magenta y cian sobre negro): mismas luces grandes que Aurora, con otra paleta. Cada estilo es una entrada de la tabla `look()` en `Theme.kt`; la luz sin color fijo toma el acento.

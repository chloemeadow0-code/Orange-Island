# Importación de PDF

橘子岛 puede extraer y enviar páginas seleccionadas de archivos PDF como imágenes a modelos con capacidad de visión.

## Cómo Funciona

1. Adjunta un archivo PDF en el chat
2. Se abre un diálogo mostrando todas las páginas como miniaturas
3. Selecciona qué páginas enviar
4. Confirma — 橘子岛 extrae las páginas como imágenes y las envía al modelo

El modelo recibe las páginas como entrada de visión, lo que le permite leer y analizar el contenido del PDF.

---

## Selección de Páginas

### Elegir Páginas

- Cada miniatura de página tiene una **casilla de verificación** en la esquina superior izquierda
- Toca la casilla para alternar una página
- Botones **Seleccionar Todas** / **Deseleccionar Todas** para operaciones masivas
- La barra superior muestra el conteo de seleccionadas (ej., "3 seleccionadas")
- Máximo **50 páginas** por PDF (los archivos más grandes se recortan)

### Vista Previa a Pantalla Completa

Toca cualquier miniatura para abrir el visor a pantalla completa:

| Gesto | Acción |
|-------|--------|
| Deslizar izquierda/derecha | Navegar entre páginas |
| Tocar | Mostrar/ocultar los controles superpuestos |
| Pellizcar | Acercar/alejar |
| Cápsula inferior izquierda | Alternar selección de página |

La vista previa te permite inspeccionar las páginas antes de decidir cuáles enviar.

---

## Enviar Páginas

Después de seleccionar las páginas, toca el botón de confirmar. 橘子岛:

1. Renderiza cada página PDF seleccionada como una imagen de alta resolución
2. Adjunta las imágenes a tu mensaje
3. Las envía al modelo (requiere un modelo con capacidad de visión)

---

## Limitaciones

- **Máximo 50 páginas** por PDF — los archivos más grandes se truncan
- **Solo imagen**: El texto no se extrae; el modelo lee las páginas visualmente
- **Se requiere modelo de visión**: El modelo de chat activo debe soportar entradas de imagen
- **Tamaño de archivo**: Aunque no hay un límite estricto de tamaño de PDF, los archivos muy grandes pueden ser lentos de renderizar

---

## Casos de Uso

- **Análisis de documentos** — sube un contrato, informe o artículo para que el modelo lo revise
- **Investigación** — comparte páginas específicas de artículos académicos
- **Traducción** — envía documentos en idiomas extranjeros para traducción
- **Resumen** — obtén resúmenes de documentos largos, página por página

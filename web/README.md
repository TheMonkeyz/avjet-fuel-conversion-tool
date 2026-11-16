# Avjet fuel conversion tool (web / PWA)

Web version of the Android app in this repo: aviation fuel volume correction to 15 °C with the
ASTM D1250-80 / API MPMS 11.1 Table 54B equations. Same features as the app: Volume (volume → mass and
mass → volume), Density chart, Density 15 °C (measured density or API gravity), English and French.

Plain static files, no build step, no dependencies, no external requests. After the first visit it works
offline (service worker), and it can be installed on a phone home screen ("Add to Home Screen" on iPhone).

| File | What |
|---|---|
| `calc.js` | The math, a port of `core/.../FuelCalculator.kt` (same constants, errors as codes) |
| `calc.test.mjs` | The tests of `FuelCalculatorTest.kt`, same values and tolerances |
| `i18n.js` | English and French text, from `app/src/main/res/values*/strings.xml` |
| `app.js`, `index.html`, `styles.css` | The screen |
| `sw.js` | Service worker: precaches every file, cache first. Bump `VERSION` when a file changes |
| `manifest.webmanifest`, `icons/` | Install data and icons |
| `tools/make-icons.mjs` | Redraws the PNG icons: `node web/tools/make-icons.mjs` |

## Run locally

Service workers and ES modules need http, not `file://`. From this folder:

```
python -m http.server 8080
```

then open <http://localhost:8080/>. All paths are relative, so it also works from a sub-path such as
`https://themonkeyz.github.io/avjet-fuel-conversion-tool/`.

## Tests

From the repo root (Node 20 or later, nothing to install):

```
node --test web/
```

or `node --test` from inside `web/`.

## Licence

GNU General Public License, version 3 or any later version. See [LICENSE](../LICENSE).

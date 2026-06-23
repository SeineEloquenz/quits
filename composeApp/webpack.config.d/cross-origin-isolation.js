// SQLite-WASM's OPFS backend needs a cross-origin-isolated context (SharedArrayBuffer).
// Serve the COOP/COEP headers from the dev server; production nginx must set the same (see web-module.nix).
;(function (config) {
    config.devServer = config.devServer || {};
    config.devServer.headers = [
        { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
        { key: 'Cross-Origin-Embedder-Policy', value: 'require-corp' },
    ];
})(config);

;(function() {
    const MiniCssExtractPlugin = require("mini-css-extract-plugin");
    config.module.rules.push({
        test: /tailwind\.css$/,
        use: [
            config.devServer ? "style-loader" : MiniCssExtractPlugin.loader,
            {
                loader: "css-loader",
                options: { sourceMap: false }
            },
            {
                loader: "postcss-loader",
                options: {
                    postcssOptions: {
                        plugins: [
                            ["@tailwindcss/postcss", {}],
                            (config.devServer ? undefined : ["cssnano", {}])
                        ]
                    }
                }
            }
        ]
    });
})();

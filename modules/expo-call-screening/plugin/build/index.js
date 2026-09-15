"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const config_plugins_1 = require("expo/config-plugins");
const withAndroidCallScreening_1 = require("./withAndroidCallScreening");
const withIosCallDirectory_1 = require("./withIosCallDirectory");
const pkg = require('../../package.json');
const withExpoCallScreening = (config, props = {}) => (0, config_plugins_1.withPlugins)(config, [
    [withIosCallDirectory_1.withIosCallDirectory, props.ios],
    [withAndroidCallScreening_1.withAndroidCallScreening, props.android],
]);
exports.default = (0, config_plugins_1.createRunOncePlugin)(withExpoCallScreening, pkg.name, pkg.version);

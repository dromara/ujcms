import gulp from 'gulp';
import { deleteAsync } from 'del';
import rename from 'gulp-rename';
import terser from 'gulp-terser';

function src(globs) {
    return gulp.src(globs, {encoding: false});
}

function copyTo(baseDir, entries) {
    return Promise.all(entries.map(({globs, destPath, minify}) => {
        let stream = src(globs);
        if (minify) {
            stream = stream.pipe(rename({suffix: '.min'})).pipe(terser());
        }
        return new Promise((resolve, reject) => {
            stream = stream.pipe(gulp.dest(baseDir + '/_files/vendor/' + destPath));
            stream.on('end', resolve);
            stream.on('finish', resolve);
            stream.on('error', reject);
        });
    }));
}

const jquery = {globs: 'node_modules/jquery/dist/**/*', destPath: 'jquery/dist/'};
const fontawesome = {globs: 'node_modules/@fortawesome/fontawesome-free/{css,webfonts}/**/*', destPath: 'fontawesome-free/'};
const axios = {globs: 'node_modules/axios/dist/**/*', destPath: 'axios/dist/'};
const jsCookie = {globs: 'node_modules/js-cookie/dist/*', destPath: 'js-cookie/dist/'};
const jqueryValidation = {globs: 'node_modules/jquery-validation/dist/**/*', destPath: 'jquery-validation/dist/'};
const jquerySerializejson = {globs: 'node_modules/jquery-serializejson/{jquery.serializejson,jquery.serializejson.min}.js', destPath: 'jquery-serializejson/'};
const es6Promise = {globs: 'node_modules/es6-promise-polyfill/promise.*', destPath: 'es6-promise-polyfill/'};
const cryptoJs = {globs: 'node_modules/crypto-js/crypto-js.js', destPath: 'crypto-js/', minify: true};
const smCrypto = {globs: 'node_modules/sm-crypto/dist/**/*', destPath: 'sm-crypto/dist/'};
const dayjs = {globs: 'node_modules/dayjs/{dayjs.min.js,locale/*}', destPath: 'dayjs/'};
const flatpickr = {globs: 'node_modules/flatpickr/dist/**/*', destPath: 'flatpickr/dist/'};
const photoswipe = {globs: 'node_modules/photoswipe/dist/**/*', destPath: 'photoswipe/dist/'};
const pdfjs = {globs: 'node_modules/pdfjs-dist/**/*', destPath: 'pdfjs-dist/'};
const fileUpload = {globs: 'node_modules/blueimp-file-upload/{css,img,js}/**/*', destPath: 'blueimp-file-upload/'};
const fileUploadMin = {globs: 'node_modules/blueimp-file-upload/js/**/*.js', destPath: 'blueimp-file-upload/js/', minify: true};
const cropperjs = {globs: 'node_modules/cropperjs/dist/**/*', destPath: 'cropperjs/dist/'};
const jqueryCropper = {globs: 'node_modules/jquery-cropper/dist/**/*', destPath: 'jquery-cropper/dist/'};
const bootstrap = {globs: 'node_modules/bootstrap/dist/**/*', destPath: 'bootstrap/dist/'};

const themeEntries = [
    jquery, fontawesome, axios, jsCookie, jqueryValidation, jquerySerializejson,
    es6Promise, cryptoJs, smCrypto, dayjs, flatpickr, photoswipe, pdfjs,
    fileUpload, fileUploadMin, cropperjs, jqueryCropper, bootstrap,
];


const errorThemeEntries = [jquery, fontawesome, bootstrap];

const theme = 'src/main/webapp/templates/1/default';
const errorTheme = 'src/main/webapp/templates/error';

async function copyTheme() {
    await copyTo(theme, themeEntries);
}


async function copyErrorTheme() {
    await copyTo(errorTheme, errorThemeEntries);
}

async function clean() {
    await deleteAsync([
        theme + '/_files/vendor',
        errorTheme + '/_files/vendor',
    ]);
}

export { clean };
export default gulp.series(clean, gulp.parallel(copyTheme, copyErrorTheme));

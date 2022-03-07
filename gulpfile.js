const gulp = require('gulp');
const del = require('del');
const sass = require('gulp-sass')(require('sass'));
const cleanCss = require('gulp-clean-css');
const sourcemaps = require('gulp-sourcemaps');
const buffer = require('vinyl-buffer');
const rename = require('gulp-rename');
const plumber = require('gulp-plumber');
const autoprefixer = require('gulp-autoprefixer');
const browserSync = require('browser-sync').create();

const theme = 'src/main/webapp/templates/1/default';
const errorTheme = 'src/main/webapp/templates/error';

function copy(done) {
  gulp.src('node_modules/jquery/dist/**/*')
    .pipe(gulp.dest(theme + '/_files/vendor/jquery/dist/'))
    .pipe(gulp.dest(errorTheme + '/_files/vendor/jquery/dist/'));
  gulp.src('node_modules/popper.js/dist/**/*')
    .pipe(gulp.dest(theme + '/_files/vendor/popper.js/dist'))
    .pipe(gulp.dest(errorTheme + '/_files/vendor/popper.js/dist'));
  gulp.src('node_modules/@fortawesome/fontawesome-free/{css,less,scss,webfonts}/*')
    .pipe(gulp.dest(theme + '/_files/vendor/fontawesome-free/'))
    .pipe(gulp.dest(errorTheme + '/_files/vendor/fontawesome-free/'));
  gulp.src('node_modules/axios/dist/**/*').pipe(gulp.dest(theme + '/_files/vendor/axios/dist/'));
  gulp.src('node_modules/es6-promise-polyfill/promise.*').pipe(gulp.dest(theme + '/_files/vendor/es6-promise-polyfill/'));
  gulp.src('node_modules/photoswipe/dist/**/*').pipe(gulp.dest(theme + '/_files/vendor/photoswipe/dist/'));
  gulp.src('node_modules/pdfjs-dist/build/**/*').pipe(gulp.dest(theme + '/_files/vendor/pdfjs-dist/build/'));
  // bootstrap 需要编译，必须确保复制完成，再进行下一个任务
  return gulp.src('node_modules/bootstrap/{dist,scss}/**/*')
    .pipe(gulp.dest(theme + '/_files/vendor/bootstrap/'))
    .pipe(gulp.dest(errorTheme + '/_files/vendor/bootstrap/'));
  // done();
}

function clean(done) {
  del.sync([theme + '/_files/vendor']);
  del.sync([errorTheme + '/_files/vendor']);
  done();
}

function css() {
  return gulp.src(theme + '/_files/scss/**/*.scss')
    .pipe(plumber())
    .pipe(sass.sync({
      outputStyle: 'expanded',
      precision: 10,
      includePaths: ['.']
    }).on('error', sass.logError))
    .pipe(autoprefixer())
    .pipe(sourcemaps.init())
    .pipe(gulp.dest(theme + '/_files/css/'))
    .pipe(cleanCss())
    .pipe(rename({suffix: '.min'}))
    .pipe(sourcemaps.write('.'))
    .pipe(buffer())
    .pipe(gulp.dest(theme + '/_files/css/'))
    .pipe(browserSync.stream());
}

function watch() {
  browserSync.init({proxy: 'localhost:8080'});
  gulp.watch(theme + '/_files/scss/**/*.scss', gulp.parallel(css));
  gulp.watch(theme + '/_files/js/**/*.js').on('change', browserSync.reload);
  gulp.watch(theme + '/**/*.html').on('change', browserSync.reload);
}

exports.watch = gulp.series(css, watch);
exports.default = gulp.series(clean, copy, css);

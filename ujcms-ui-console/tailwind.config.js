/** @type {import('tailwindcss').Config} */
import colors from 'tailwindcss/colors';

export default {
  important: true,
  content: ['./src/**/*.{vue,ts}'],
  theme: {
    fontFamily: {
      sans: [
        'ui-sans-serif',
        'system-ui',
        '-apple-system',
        'BlinkMacSystemFont',
        '"Segoe UI"',
        'Roboto',
        '"Helvetica Neue"',
        'Arial',
        '"Noto Sans"',
        '"PingFang SC"',
        '"Hiragino Sans GB"',
        '"Microsoft YaHei"',
        '"WenQuanYi Micro Hei"',
        'sans-serif',
        '"Apple Color Emoji"',
        '"Segoe UI Emoji"',
        '"Segoe UI Symbol"',
        '"Noto Color Emoji"',
      ],
    },
    screens: {
      // sm: '768px',
      md: '992px',
      // lg: '1200px',
      xl: '1536px',
    },
    colors: {
      transparent: 'transparent',
      current: 'currentColor',
      black: colors.black,
      white: colors.white,
      gray: {
        ...colors.gray,
        // 五个文字色引用 Element Plus 的文字色变量（取值与原字面量一致：#303133 / #606266 / #909399 / #a8abb2 / #c0c4cc），
        // 文字随 EP 的 :root 变量走，将来的深色样式表只需改变量。同 primary：变量值不支持 gray-primary/NN 透明度修饰符。
        // 面色（bg-white、gray-50…900）仍是字面量（console-shell S6）。
        primary: 'var(--el-text-color-primary)',
        regular: 'var(--el-text-color-regular)',
        secondary: 'var(--el-text-color-secondary)',
        placeholder: 'var(--el-text-color-placeholder)',
        disabled: 'var(--el-text-color-disabled)',
      },
      // 主色引用 Element Plus 的主色变量链（src/utils/theme.ts 在 :root 上改写），text-primary 等随主题色变化。
      // 取值与原字面量一致（#409eff / light-5 #a0cfff / light-9 #ecf5ff）。变量值不支持 primary/NN 透明度修饰符，勿用。
      primary: {
        DEFAULT: 'var(--el-color-primary)',
        light: 'var(--el-color-primary-light-5)',
        lighter: 'var(--el-color-primary-light-9)',
      },
      success: {
        DEFAULT: '#67c23a',
        light: '#b3e19d',
        lighter: '#f0f9eb',
      },
      warning: {
        DEFAULT: '#e6a23c',
        light: '#f3d19e',
        lighter: '#fdf6ec',
      },
      danger: {
        DEFAULT: '#f56c6c',
        light: '#fab6b6',
        lighter: '#fef0f0',
      },
      purple: {
        DEFAULT: colors.purple[500],
        light: colors.purple[300],
        lighter: colors.purple[100],
      },
      secondary: {
        DEFAULT: '#909399',
        light: '#c8c9cc',
        lighter: '#f4f4f5',
      },
    },
    extend: {
      transitionDuration: {
        // vue 文档中提到，过度效果的时间一般在0.1s-0.4s之间，而0.25s会是一个比较好的值
        // https://v3.vuejs.org/guide/transitions-overview.html#timing
        250: '250ms',
        350: '350ms',
      },
      transitionProperty: {
        width: 'width',
        margin: 'margin',
      },
      // el-menu 展开时最小宽度是 200px
      // el-menu 折叠时宽度是 64px
      width: {
        sidebar: '200px',
        'sidebar-collapse': '64px',
      },
      margin: {
        sidebar: '200px',
        'sidebar-collapse': '64px',
      },
    },
  },
  variants: {
    // extend: {
    //   borderStyle: ['hover'],
    // },
  },
  plugins: [],
  // corePlugins: {
  //   preflight: false,
  // },
};

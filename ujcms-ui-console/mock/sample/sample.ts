import { MockMethod } from 'vite-plugin-mock';
export default [
  {
    url: '/sample',
    method: 'get',
    // response: ({ query, body }: any) => {
    response: () => {
      return {
        code: 0,
        message: 'ok',
        data: {
          total: 2,
          list: [
            {
              id: 100,
              title: 'Mock测试数据100',
            },
            {
              id: 101,
              title: 'Mock测试数据101',
            },
          ],
        },
      };
    },
  },
] as MockMethod[];

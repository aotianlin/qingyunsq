import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/pages/Home.vue'),
    // 首页允许游客访问，去除 requiresAuth
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/Login.vue'),
    meta: { guest: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/pages/Register.vue'),
    meta: { guest: true },
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/pages/ForgotPassword.vue'),
    meta: { guest: true },
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    children: [
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/pages/Profile.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'users/:id',
        name: 'user-page',
        component: () => import('@/pages/UserPage.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'users/:id/follows',
        name: 'follows-list',
        component: () => import('@/pages/FollowsList.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'square',
        name: 'square',
        component: () => import('@/pages/Square.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'posts/new',
        name: 'post-create',
        component: () => import('@/pages/PostCreate.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'posts/:id',
        name: 'post-detail',
        component: () => import('@/pages/PostDetail.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'spaces',
        name: 'spaces',
        component: () => import('@/pages/Spaces.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'spaces/new',
        name: 'space-create',
        component: () => import('@/pages/SpaceCreate.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'checkin',
        name: 'checkin',
        component: () => import('@/pages/CheckinChallenges.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'checkin/new',
        name: 'checkin-create',
        component: () => import('@/pages/CheckinChallengeCreate.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'checkin/:id',
        name: 'checkin-detail',
        component: () => import('@/pages/CheckinChallengeDetail.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'resources',
        name: 'resources',
        component: () => import('@/pages/Resources.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'resources/upload',
        name: 'resource-upload',
        component: () => import('@/pages/ResourceUpload.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'resources/:id',
        name: 'resource-detail',
        component: () => import('@/pages/ResourceDetail.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('@/pages/Search.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'points',
        name: 'points',
        component: () => import('@/pages/Points.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'ai',
        name: 'ai-assistant',
        component: () => import('@/pages/AiAssistant.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'ai/wikis',
        name: 'ai-wikis',
        component: () => import('@/pages/AiAssistant.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'ai/discover',
        name: 'ai-discover',
        component: () => import('@/pages/AiAssistant.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'ai/wikis/:id',
        name: 'ai-wiki-detail',
        component: () => import('@/pages/AiAssistant.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/pages/Notifications.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/pages/Messages.vue'),
        meta: { requiresAuth: true },
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/pages/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'admin-dashboard',
        component: () => import('@/pages/admin/AdminDashboard.vue'),
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/pages/admin/AdminUsers.vue'),
      },
      {
        path: 'posts',
        name: 'admin-posts',
        component: () => import('@/pages/admin/AdminPosts.vue'),
      },
      {
        path: 'spaces',
        name: 'admin-spaces',
        component: () => import('@/pages/admin/AdminSpaces.vue'),
      },
      {
        path: 'audit-logs',
        name: 'admin-audit-logs',
        component: () => import('@/pages/admin/AdminAuditLog.vue'),
      },
      {
        path: 'reports',
        name: 'admin-reports',
        component: () => import('@/pages/admin/AdminReports.vue'),
      },
      {
        path: 'sensitive-words',
        name: 'admin-sensitive-words',
        component: () => import('@/pages/admin/AdminSensitiveWords.vue'),
      },
      {
        path: 'tenants',
        name: 'admin-tenants',
        component: () => import('@/pages/admin/AdminTenants.vue'),
      },
      {
        path: 'ai-config',
        name: 'admin-ai-config',
        component: () => import('@/pages/admin/AdminAiConfig.vue'),
      },
    ],
  },
  {
    path: '/spaces/:id',
    name: 'space-detail',
    component: () => import('@/pages/SpaceDetail.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/pages/NotFound.vue'),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

function clearStoredAuth() {
  localStorage.removeItem('token');
  localStorage.removeItem('role');
  localStorage.removeItem('tenantId');
  localStorage.removeItem('tenantCode');
}

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');

  const isGuest = role === 'GUEST';
  const allowedGuestPaths = [
    '/',
    '/square',
    '/spaces',
    '/resources',
    '/login',
    '/register',
    '/forgot-password',
  ];
  const isAllowedGuestPath =
    allowedGuestPaths.includes(to.path) ||
    (to.path.startsWith('/posts/') && to.path !== '/posts/new') ||
    to.path.startsWith('/spaces/');

  if (to.meta.guest) {
    if (token) {
      clearStoredAuth();
    }
    next();
  } else if (to.meta.requiresAuth && !token) {
    next('/login');
  } else if (isGuest && to.meta.requiresAuth && !isAllowedGuestPath) {
    next('/login');
  } else if (to.path === '/' && token && !isGuest) {
    next('/square');
  } else if (to.meta.requiresAdmin && role !== 'TENANT_ADMIN' && role !== 'SUPER_ADMIN') {
    next('/');
  } else {
    next();
  }
});

export default router;

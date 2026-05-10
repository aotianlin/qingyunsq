import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/pages/Home.vue'),
    meta: { requiresAuth: true },
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
    path: '/profile',
    name: 'profile',
    component: () => import('@/pages/Profile.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/users/:id',
    name: 'user-page',
    component: () => import('@/pages/UserPage.vue'),
  },
  {
    path: '/square',
    name: 'square',
    component: () => import('@/pages/Square.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/posts/new',
    name: 'post-create',
    component: () => import('@/pages/PostCreate.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/posts/:id',
    name: 'post-detail',
    component: () => import('@/pages/PostDetail.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/spaces',
    name: 'spaces',
    component: () => import('@/pages/Spaces.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/spaces/new',
    name: 'space-create',
    component: () => import('@/pages/SpaceCreate.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/spaces/:id',
    name: 'space-detail',
    component: () => import('@/pages/SpaceDetail.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/checkin',
    name: 'checkin',
    component: () => import('@/pages/CheckinChallenges.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/checkin/new',
    name: 'checkin-create',
    component: () => import('@/pages/CheckinChallengeCreate.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/checkin/:id',
    name: 'checkin-detail',
    component: () => import('@/pages/CheckinChallengeDetail.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/resources',
    name: 'resources',
    component: () => import('@/pages/Resources.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/resources/upload',
    name: 'resource-upload',
    component: () => import('@/pages/ResourceUpload.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/resources/:id',
    name: 'resource-detail',
    component: () => import('@/pages/ResourceDetail.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('@/pages/Notifications.vue'),
    meta: { requiresAuth: true },
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
    ],
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

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');

  if (to.meta.requiresAuth && !token) {
    next('/login');
  } else if (to.meta.guest && token) {
    next('/');
  } else if (to.meta.requiresAdmin && role !== 'TENANT_ADMIN' && role !== 'SUPER_ADMIN') {
    next('/');
  } else {
    next();
  }
});

export default router;

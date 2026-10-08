import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../pages/HomePage.vue'
import ProfilePage from '../pages/ProfilePage.vue'
import InterviewPage from '../pages/InterviewPage.vue'
import ReviewPage from '../pages/ReviewPage.vue'

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/profile', name: 'profile', component: ProfilePage },
    { path: '/interviews/:id', name: 'interview', component: InterviewPage },
    { path: '/interviews/:id/review', name: 'review', component: ReviewPage },
  ],
})

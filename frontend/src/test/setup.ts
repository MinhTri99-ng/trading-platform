import '@testing-library/jest-dom/vitest';

Object.defineProperty(window, 'ResizeObserver', {
  writable: true,
  value: class ResizeObserver {
    observe() {}
    disconnect() {}
    unobserve() {}
  },
});

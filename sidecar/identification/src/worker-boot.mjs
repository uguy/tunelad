// A worker thread does not inherit the TypeScript loader of the main thread: register it, then run the worker.
import { register } from 'tsx/esm/api';

register();
await import('./worker.ts');

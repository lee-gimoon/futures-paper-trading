import { createContext } from 'react';

import type { AuthStatus, User } from '@/types/auth';

export type AuthContextValue = {
  user: User | null;
  status: AuthStatus;
  error: string | null;
  clearError: () => void;
  login: (email: string, password: string) => Promise<void>;
  signup: (
    email: string,
    password: string,
    displayName: string,
  ) => Promise<void>;
  logout: () => Promise<void>;
  expireSession: () => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

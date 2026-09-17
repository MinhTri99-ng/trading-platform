/* eslint-disable react-refresh/only-export-components -- Provider, hook, and auth types form one public API. */
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

export type User = { id: string; firstName: string; lastName: string; username: string; email: string; phone: string };
export type RegisterInput = Omit<User, "id"> & { password: string };
export type LoginInput = { identifier: string; password: string };
export type ProfileInput = Pick<User, "firstName" | "lastName" | "email" | "phone">;
export type AuthResult = { success: boolean; error?: string };
type StoredUser = User & { password: string };
type AuthContextValue = { user: User | null; isAuthenticated: boolean; login: (input: LoginInput) => AuthResult; register: (input: RegisterInput) => AuthResult; logout: () => void; updateProfile: (input: ProfileInput) => AuthResult; changePassword: (currentPassword: string, newPassword: string) => AuthResult; deleteAccount: () => AuthResult };

const USERS_KEY = "tradeai-pro-users";
const SESSION_KEY = "tradeai-pro-session";
const AuthContext = createContext<AuthContextValue | null>(null);
const normalize = (value: string) => value.trim().toLowerCase();
const publicUser = (stored: StoredUser): User => ({ id: stored.id, firstName: stored.firstName, lastName: stored.lastName, username: stored.username, email: stored.email, phone: stored.phone });
const readUsers = (): StoredUser[] => { try { const value = localStorage.getItem(USERS_KEY); return value ? (JSON.parse(value) as StoredUser[]) : []; } catch { return []; } };
const writeUsers = (users: StoredUser[]) => { try { localStorage.setItem(USERS_KEY, JSON.stringify(users)); } catch { /* Storage may be blocked. */ } };

// The context owns the mock database and session so every auth surface stays in sync after reload.
export function AuthProvider({ children }: { children: ReactNode }) {
  const [users, setUsers] = useState<StoredUser[]>(readUsers);
  const [user, setUser] = useState<User | null>(() => {
    try { const sessionId = localStorage.getItem(SESSION_KEY); const stored = readUsers().find((candidate) => candidate.id === sessionId); return stored ? publicUser(stored) : null; } catch { return null; }
  });
  useEffect(() => { writeUsers(users); }, [users]);
  const setSession = (nextUser: User | null) => { setUser(nextUser); try { if (nextUser) localStorage.setItem(SESSION_KEY, nextUser.id); else localStorage.removeItem(SESSION_KEY); } catch { /* Storage may be blocked. */ } };
  const register = (input: RegisterInput): AuthResult => { if (users.some((candidate) => normalize(candidate.email) === normalize(input.email) || normalize(candidate.username) === normalize(input.username))) return { success: false, error: "Tài khoản đã tồn tại trong hệ thống, vui lòng đăng nhập!" }; const nextUser: StoredUser = { ...input, id: crypto.randomUUID() }; setUsers((current) => [...current, nextUser]); setSession(publicUser(nextUser)); return { success: true }; };
  const login = (input: LoginInput): AuthResult => { const candidate = users.find((item) => normalize(item.username) === normalize(input.identifier) || normalize(item.email) === normalize(input.identifier)); if (!candidate || candidate.password !== input.password) return { success: false, error: "Tài khoản không tồn tại hoặc mật khẩu không chính xác." }; setSession(publicUser(candidate)); return { success: true }; };
  const updateProfile = (input: ProfileInput): AuthResult => { if (!user) return { success: false, error: "Phiên đăng nhập đã hết hạn." }; if (users.some((candidate) => candidate.id !== user.id && normalize(candidate.email) === normalize(input.email))) return { success: false, error: "Email đã được sử dụng bởi tài khoản khác." }; const updated = { ...user, ...input }; setUsers((current) => current.map((candidate) => candidate.id === user.id ? { ...candidate, ...input } : candidate)); setSession(updated); return { success: true }; };
  const changePassword = (currentPassword: string, newPassword: string): AuthResult => { if (!user) return { success: false, error: "Phiên đăng nhập đã hết hạn." }; const stored = users.find((candidate) => candidate.id === user.id); if (!stored || stored.password !== currentPassword) return { success: false, error: "Mật khẩu hiện tại không chính xác." }; setUsers((current) => current.map((candidate) => candidate.id === user.id ? { ...candidate, password: newPassword } : candidate)); return { success: true }; };
  const deleteAccount = (): AuthResult => { if (!user) return { success: false, error: "Phiên đăng nhập đã hết hạn." }; setUsers((current) => current.filter((candidate) => candidate.id !== user.id)); setSession(null); return { success: true }; };
  const value = { user, isAuthenticated: Boolean(user), login, register, logout: () => setSession(null), updateProfile, changePassword, deleteAccount };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() { const context = useContext(AuthContext); if (!context) throw new Error("useAuth must be used inside AuthProvider"); return context; }
import { useState } from 'react';
import { api, refreshCsrf } from './api';
import { Brand, Field, FormError, Icon } from './components';

export default function Auth({ onLogin, config }) {
  const [register, setRegister] = useState(false);
  const [values, setValues] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const change = (key, value) => {
    setValues((previous) => ({ ...previous, [key]: value }));
    setFields((previous) => ({ ...previous, [key]: '' }));
  };
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setFields({});
    try {
      if (register) {
        await api('/api/auth/register', { method: 'POST', body: values });
        setRegister(false);
        setNotice('Your student account is ready. Sign in to make your first reservation.');
      } else {
        await api('/api/auth/login', {
          method: 'POST',
          body: { email: values.email, password: values.password },
          form: true,
        });
        await refreshCsrf();
        onLogin(await api('/api/auth/me'));
      }
    } catch (failure) {
      setError(failure.message);
      setFields(failure.fields || {});
    } finally {
      setBusy(false);
    }
  }
  async function demoSignIn(email, password) {
    setBusy(true);
    setError('');
    try {
      await api('/api/auth/login', { method: 'POST', form: true, body: { email, password } });
      await refreshCsrf();
      onLogin(await api('/api/auth/me'));
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-layout">
      <aside className="auth-story">
        <Brand />
        <div className="auth-copy">
          <span className="eyebrow">CAMPUS EQUIPMENT DESK</span>
          <h1>
            Good equipment.
            <br />
            Great possibilities.
          </h1>
          <p>
            Find the tools for your next project. Reserve a time, collect your kit, and bring your
            ideas into the room.
          </p>
        </div>
      </aside>
      <main className="auth-main">
        <div className="auth-form">
          <div className="auth-mobile-brand">
            <Brand />
          </div>
          <span className="eyebrow">
            {register ? 'JOIN THE EQUIPMENT DESK' : 'YOUR CAMPUS WORKSPACE'}
          </span>
          <h2>{register ? 'Create your account' : 'Welcome back'}</h2>
          <p className="auth-intro">
            {register
              ? 'Student access to equipment, reservations and loan records.'
              : 'Sign in to reserve equipment and manage your loans.'}
          </p>
          {notice && (
            <div className="form-notice" role="status">
              {notice}
            </div>
          )}
          <FormError error={error} />
          <form onSubmit={submit}>
            {!register && config?.demoEnabled && (
              <button
                type="button"
                className="button secondary demo-fill"
                onClick={() => {
                  setValues({ fullName: '', email: 'alex@campus.test', password: 'Student123!' });
                  setFields({});
                  setError('');
                  setNotice('Student sign-in details filled. Select Sign in to continue.');
                }}
              >
                Fill student credentials
              </button>
            )}
            {register && config?.demoEnabled && (
              <button
                type="button"
                className="button secondary demo-fill"
                onClick={() => {
                  setValues({
                    fullName: 'Casey Demo',
                    email: `casey-${Date.now().toString(36)}@campus.test`,
                    password: 'Student123!',
                  });
                  setFields({});
                  setError('');
                }}
              >
                Fill example account
              </button>
            )}
            {register && (
              <Field label="Full name" error={fields.fullName}>
                <input
                  autoComplete="name"
                  required
                  minLength={2}
                  maxLength={80}
                  value={values.fullName}
                  onChange={(event) => change('fullName', event.target.value)}
                  placeholder="Your full name"
                />
              </Field>
            )}
            <Field label="Email address" error={fields.email}>
              <input
                type="email"
                autoComplete="username"
                required
                maxLength={120}
                value={values.email}
                onChange={(event) => change('email', event.target.value)}
                placeholder="you@campus.edu"
              />
            </Field>
            <Field
              label="Password"
              error={fields.password}
              hint={register ? '10-72 characters, including a letter and a number.' : undefined}
            >
              <input
                type="password"
                autoComplete={register ? 'new-password' : 'current-password'}
                required
                minLength={register ? 10 : undefined}
                maxLength={72}
                value={values.password}
                onChange={(event) => change('password', event.target.value)}
                placeholder={register ? 'Choose a password' : 'Enter your password'}
              />
            </Field>
            <button className="button primary auth-submit" disabled={busy}>
              {busy ? 'Please wait...' : register ? 'Create student account' : 'Sign in'}
              <Icon name="arrow" size={18} />
            </button>
          </form>
          <p className="auth-switch">
            {register ? 'Already have an account?' : 'New to EquiFlow?'}{' '}
            <button
              className="text-button"
              onClick={() => {
                setRegister(!register);
                setError('');
                setFields({});
                setNotice('');
              }}
            >
              {register ? 'Sign in' : 'Create an account'}
            </button>
          </p>
          {config?.demoEnabled && (
            <div className="demo-access">
              <span className="eyebrow">EXPLORE THE DEMO</span>
              <p>Sign in with one click using a seeded demonstration account.</p>
              <div>
                <button
                  className="button secondary"
                  disabled={busy}
                  onClick={() => demoSignIn('alex@campus.test', 'Student123!')}
                >
                  Student Alex
                </button>
                <button
                  className="button secondary"
                  disabled={busy}
                  onClick={() => demoSignIn('jordan@campus.test', 'Student123!')}
                >
                  Student Jordan
                </button>
                <button
                  className="button secondary"
                  disabled={busy}
                  onClick={() => demoSignIn('desk@campus.test', 'DeskAdmin123!')}
                >
                  <Icon name="shield" size={17} />
                  Administrator
                </button>
              </div>
            </div>
          )}
          <div className="auth-security">
            <Icon name="shield" size={16} />
            <span>Your session stays in this browser.</span>
          </div>
        </div>
      </main>
    </div>
  );
}

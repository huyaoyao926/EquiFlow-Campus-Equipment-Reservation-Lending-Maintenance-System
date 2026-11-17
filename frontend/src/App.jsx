import { useCallback, useEffect, useRef, useState } from 'react';
import { api, moment, refreshCsrf } from './api';
import { Brand, Empty, Icon, Loading } from './components';
import Auth from './Auth';
import Dashboard from './Dashboard';
import Catalog from './Catalog';
import Requests from './Requests';
import Activity from './Activity';
import { useNavigate, useLocation } from 'react-router-dom';
import { Maintenance, Presentation } from './Operations';

const titles = {
  maintenance: ['Maintenance', 'Inspect, repair and return equipment to service.'],
  presentation: ['Presentation lab', 'Live service status and event recovery.'],
  overview: ['Overview', 'A place for every reservation.'],
  equipment: ['Equipment catalogue', 'Find the right tools for your next project.'],
  requests: ['Requests & loans', 'Every reservation, from request to return.'],
  activity: ['Desk activity', 'A record of decisions and equipment changes.'],
};

function readRoute() {
  const fragment = window.location.hash.slice(1);
  const [page, params] = (fragment.startsWith('/') ? fragment.slice(1) : fragment).split('?');
  const query = new URLSearchParams(params);
  return {
    page: titles[page] ? page : 'overview',
    id: query.get('id') ? Number(query.get('id')) : null,
    status: query.get('status') || '',
  };
}

export default function App() {
  const navigate = useNavigate();
  const location = useLocation();
  useEffect(() => {
    setRoute(readRoute());
  }, [location]);
  const [user, setUser] = useState(null);
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [startupError, setStartupError] = useState('');
  const [route, setRoute] = useState(readRoute);
  const [refresh, setRefresh] = useState(0);
  const [toast, setToast] = useState(null);
  const [notes, setNotes] = useState({ items: [], unread: 0 });
  const [showNotes, setShowNotes] = useState(false);
  const timer = useRef();
  const noticeRef = useRef();
  const notify = useCallback((message, type = 'success') => {
    clearTimeout(timer.current);
    setToast({ message, type });
    timer.current = setTimeout(() => setToast(null), 6000);
  }, []);
  const changed = useCallback(() => setRefresh((value) => value + 1), []);
  function go(page, id = null, status = '') {
    const params = new URLSearchParams();
    if (id) params.set('id', id);
    if (status) params.set('status', status);
    navigate(`/${page}${params.size ? `?${params}` : ''}`);
    setShowNotes(false);
  }
  useEffect(() => {
    Promise.all([
      api('/api/config'),
      refreshCsrf(),
      api('/api/auth/me', { silent: true }).catch((error) => {
        if (error.status === 401) return null;
        throw error;
      }),
    ])
      .then(([settings, , account]) => {
        setConfig(settings);
        setUser(account);
      })
      .catch((error) => setStartupError(error.message))
      .finally(() => setLoading(false));
    const hash = () => {
      setRoute(readRoute());
      window.scrollTo({ top: 0 });
    };
    const expired = () => {
      setUser(null);
      setShowNotes(false);
      notify('Your session has ended. Please sign in again.', 'error');
    };
    window.addEventListener('hashchange', hash);
    window.addEventListener('session-expired', expired);
    return () => {
      window.removeEventListener('hashchange', hash);
      window.removeEventListener('session-expired', expired);
      clearTimeout(timer.current);
    };
  }, [notify]);
  useEffect(() => {
    if (!user) return;
    let active = true;
    const loadNotes = () =>
      api('/api/notifications')
        .then((data) => {
          if (active) setNotes(data);
        })
        .catch(() => {});
    loadNotes();
    const poll = setInterval(loadNotes, 3000);
    return () => {
      active = false;
      clearInterval(poll);
    };
  }, [user, refresh]);
  useEffect(() => {
    function outside(event) {
      if (noticeRef.current && !noticeRef.current.contains(event.target)) setShowNotes(false);
    }
    function escape(event) {
      if (event.key === 'Escape') setShowNotes(false);
    }
    document.addEventListener('mousedown', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('mousedown', outside);
      document.removeEventListener('keydown', escape);
    };
  }, []);
  async function logout() {
    try {
      await api('/api/auth/logout', { method: 'POST' });
      await refreshCsrf();
      setUser(null);
      setShowNotes(false);
      setNotes({ items: [], unread: 0 });
      go('overview');
    } catch (error) {
      notify(error.message, 'error');
    }
  }
  async function openNotification(note) {
    try {
      if (!note.readAt) await api(`/api/notifications/${note.id}/read`, { method: 'PATCH' });
      changed();
      go('requests', note.reservationId);
    } catch (error) {
      notify(error.message, 'error');
    }
  }
  if (loading)
    return (
      <div className="startup">
        <Brand />
        <Loading label="Opening the equipment desk..." />
      </div>
    );
  if (startupError)
    return (
      <div className="startup">
        <Brand />
        <Empty
          title="The desk could not be reached"
          icon="info"
          action={
            <button className="button primary" onClick={() => window.location.reload()}>
              Try again
            </button>
          }
        >
          {startupError}
        </Empty>
      </div>
    );
  const toastView = toast && (
    <div className={`toast toast-${toast.type}`} role="status">
      <Icon name={toast.type === 'error' ? 'info' : 'check'} size={19} />
      <span>{toast.message}</span>
      <button className="icon-button" aria-label="Dismiss message" onClick={() => setToast(null)}>
        <Icon name="close" size={16} />
      </button>
    </div>
  );
  if (!user)
    return (
      <>
        <Auth
          onLogin={(account) => {
            setUser(account);
            changed();
            go('overview');
          }}
          config={config}
        />
        {toastView}
      </>
    );
  const admin = user.role === 'ADMIN';
  const currentPage =
    ['activity', 'maintenance', 'presentation'].includes(route.page) && !admin
      ? 'overview'
      : route.page;
  const navigation = [
    ['overview', 'home', 'Overview'],
    ['equipment', 'box', 'Equipment'],
    ['requests', 'calendar', admin ? 'Requests & loans' : 'My requests'],
    ...(admin
      ? [
          ['maintenance', 'box', 'Maintenance'],
          ['presentation', 'activity', 'Presentation lab'],
          ['activity', 'activity', 'Activity log'],
        ]
      : []),
  ];
  return (
    <div className="app-shell">
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById('main-content')?.focus();
        }}
      >
        Skip to content
      </a>
      <aside className="sidebar">
        <Brand />
        <div className="workspace-label">{admin ? 'DESK WORKSPACE' : 'STUDENT WORKSPACE'}</div>
        <nav aria-label="Main navigation">
          {navigation.map(([key, icon, label]) => (
            <a
              key={key}
              href={`#/${key}`}
              className={`nav-link ${currentPage === key ? 'active' : ''}`}
              aria-current={currentPage === key ? 'page' : undefined}
            >
              <Icon name={icon} size={20} />
              <span>{label}</span>
              {currentPage === key && <i />}
            </a>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="desk-card">
            <Icon name="shield" size={20} />
            <strong>Campus equipment desk</strong>
            <p>
              Collect with your student card.
              <br />
              Return every item in its kit.
            </p>
          </div>
          <div className="sidebar-user">
            <span className="avatar">
              {user.fullName
                .split(' ')
                .map((word) => word[0])
                .slice(0, 2)
                .join('')}
            </span>
            <div>
              <strong>{user.fullName}</strong>
              <span>{admin ? 'Administrator' : 'Student account'}</span>
            </div>
            <button className="icon-button" aria-label="Sign out" onClick={logout}>
              <Icon name="logout" size={19} />
            </button>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span className="topbar-context">
            CAMPUS RESOURCES <span>/</span> {admin ? 'Administration' : 'Equipment desk'}
          </span>
          <div className="topbar-actions">
            <span className="campus-date">
              {new Intl.DateTimeFormat('en-GB', {
                weekday: 'short',
                day: 'numeric',
                month: 'short',
                timeZone: 'Asia/Singapore',
              }).format(new Date())}
            </span>
            <button className="icon-button" aria-label="Refresh workspace" onClick={changed}>
              <Icon name="refresh" size={19} />
            </button>
            <div className="notification-anchor" ref={noticeRef}>
              <button
                className="icon-button notification-button"
                aria-label={`Notifications, ${notes.unread} unread`}
                aria-expanded={showNotes}
                onClick={() => setShowNotes((value) => !value)}
              >
                <Icon name="bell" size={21} />
                {notes.unread > 0 && (
                  <span className="notification-count">{Math.min(notes.unread, 99)}</span>
                )}
              </button>
              {showNotes && (
                <section className="notification-panel" aria-label="Notifications">
                  <div className="notification-heading">
                    <strong>Updates for you</strong>
                    <span>{notes.unread} unread</span>
                  </div>
                  {notes.items.length ? (
                    notes.items.map((note) => (
                      <button
                        key={note.id}
                        className={`notification-item ${note.readAt ? '' : 'unread'}`}
                        onClick={() => openNotification(note)}
                      >
                        <strong>{note.title}</strong>
                        <span>{note.message}</span>
                        <small>{moment(note.createdAt)}</small>
                      </button>
                    ))
                  ) : (
                    <p className="notification-empty">Your reservation updates will appear here.</p>
                  )}
                </section>
              )}
            </div>
            {config.demoEnabled && (
              <select
                aria-label="Switch demo account"
                value=""
                onChange={async (e) => {
                  const email = e.target.value;
                  if (!email) return;
                  try {
                    await api('/api/auth/login', {
                      method: 'POST',
                      form: true,
                      body: {
                        email,
                        password: email.startsWith('desk') ? 'DeskAdmin123!' : 'Student123!',
                      },
                    });
                    await refreshCsrf();
                    setToast(null);
                    setUser(await api('/api/auth/me'));
                    setNotes({ items: [], unread: 0 });
                    changed();
                    go('overview');
                  } catch (error) {
                    notify(error.message, 'error');
                  }
                }}
              >
                <option value="">Switch demo role</option>
                <option value="alex@campus.test">Student Alex</option>
                <option value="jordan@campus.test">Student Jordan</option>
                <option value="desk@campus.test">Administrator</option>
              </select>
            )}
            <span className="role-tag">{admin ? 'Desk admin' : 'Student'}</span>
          </div>
        </header>
        <main className="main-content" id="main-content" tabIndex={-1}>
          <div className="page-heading">
            <div>
              <h1>
                {currentPage === 'requests' && !admin
                  ? 'My requests & loans'
                  : titles[currentPage][0]}
              </h1>
              <p>{titles[currentPage][1]}</p>
            </div>
            <span className="page-reference">
              EquiFlow /{' '}
              {String(navigation.findIndex(([key]) => key === currentPage) + 1).padStart(2, '0')}
            </span>
          </div>
          {currentPage === 'overview' && (
            <Dashboard user={user} refresh={refresh} go={go} config={config} />
          )}
          {currentPage === 'equipment' && (
            <Catalog
              user={user}
              config={config}
              refresh={refresh}
              onChange={changed}
              notify={notify}
            />
          )}
          {currentPage === 'requests' && (
            <Requests
              user={user}
              config={config}
              refresh={refresh}
              onChange={changed}
              notify={notify}
              focusId={route.id}
              initialStatus={route.status}
              go={go}
            />
          )}
          {currentPage === 'maintenance' && (
            <Maintenance config={config} notify={notify} onChange={changed} />
          )}
          {currentPage === 'presentation' && <Presentation notify={notify} />}
          {currentPage === 'activity' && <Activity refresh={refresh} />}
        </main>
        <footer className="workspace-footer">
          <span>EquiFlow / Campus equipment, accounted for.</span>
          <span>Campus time: Singapore (UTC+08:00)</span>
        </footer>
      </div>
      {toastView}
    </div>
  );
}

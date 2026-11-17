import { useEffect, useState } from 'react';
import { api, moment } from './api';
import { FormError, Loading } from './components';

export function Maintenance({ config, notify, onChange }) {
  const [jobs, setJobs] = useState(null);
  const [notes, setNotes] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(null);
  async function load() {
    try {
      setJobs(await api('/api/admin/maintenance'));
      setError('');
    } catch (e) {
      setError(e.message);
    }
  }
  useEffect(() => {
    load();
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, []);
  async function complete(job) {
    setBusy(job.id);
    setError('');
    try {
      await api(`/api/admin/maintenance/${job.id}/complete`, {
        method: 'POST',
        body: { version: job.version, resolution: notes[job.id] || '' },
      });
      notify('Repair completed. The equipment service will restore availability through Kafka.');
      onChange();
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(null);
    }
  }
  return (
    <section className="panel operations-panel">
      <p>
        Damaged returns and desk maintenance requests create repair jobs automatically. Completed
        repairs restore equipment availability.
      </p>
      <FormError error={error} />
      {!jobs ? (
        <Loading />
      ) : !jobs.length ? (
        <p>
          No maintenance jobs yet. Record a damaged return or place an available asset in
          maintenance.
        </p>
      ) : (
        jobs.map((job) => (
          <article className="repair-card" key={job.id}>
            <div className="repair-heading">
              <h2>{job.equipmentName}</h2>
              <span
                className={`badge ${job.status === 'OPEN' ? 'badge-maintenance' : 'badge-returned'}`}
              >
                {job.status}
              </span>
            </div>
            <p>{job.reason}</p>
            <small>
              Job {job.id} / {moment(job.createdAt)}
            </small>
            {job.status === 'OPEN' ? (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  complete(job);
                }}
              >
                <label className="field">
                  <span>Repair resolution</span>
                  <textarea
                    required
                    minLength={10}
                    maxLength={500}
                    value={notes[job.id] || ''}
                    onChange={(e) => setNotes({ ...notes, [job.id]: e.target.value })}
                  />
                </label>
                <div className="demo-toolbar">
                  {config.demoEnabled && (
                    <button
                      type="button"
                      className="button secondary"
                      onClick={() =>
                        setNotes({
                          ...notes,
                          [job.id]:
                            'Inspected all components, replaced the damaged part and passed the operational safety check.',
                        })
                      }
                    >
                      Fill example resolution
                    </button>
                  )}
                  <button className="button primary" disabled={busy === job.id}>
                    Complete repair
                  </button>
                </div>
              </form>
            ) : (
              <p>
                <strong>Resolution:</strong> {job.resolution}
              </p>
            )}
          </article>
        ))
      )}
    </section>
  );
}

export function Presentation({ notify }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function load() {
    try {
      setData(await api('/api/admin/services'));
      setError('');
    } catch (e) {
      setError(e.message);
    }
  }
  useEffect(() => {
    load();
    const timer = setInterval(load, 2500);
    return () => clearInterval(timer);
  }, []);
  async function action(path, message) {
    setBusy(true);
    try {
      await api(path, { method: 'POST' });
      notify(message);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  const paused = data?.notification?.paused;
  return (
    <section className="panel operations-panel">
      <div className="alert alert-info">
        Live presentation controls. Pause notification consumption, change a reservation, then
        resume to demonstrate Kafka recovery. Replay an event to demonstrate duplicate protection.
      </div>
      <FormError error={error} />
      {!data ? (
        <Loading />
      ) : (
        <>
          <div className="row g-3 mb-4">
            {[{ name: data.lendingServiceName, status: 'UP' }, ...data.services].map((service) => (
              <div className="col-md-3" key={service.name}>
                <div className="service-card">
                  <strong>{service.name}</strong>
                  <span>{service.status}</span>
                </div>
              </div>
            ))}
          </div>
          <div className="demo-toolbar">
            <button
              className="button primary"
              disabled={busy || data.notification?.unavailable}
              onClick={() =>
                action(
                  `/api/admin/services/notifications/${paused ? 'resume' : 'pause'}`,
                  paused
                    ? 'Notification consumption resumed.'
                    : 'Notification consumption paused. Reservations still work.',
                )
              }
            >
              {paused ? 'Resume notifications' : 'Pause notifications'}
            </button>
            <button className="button secondary" onClick={load}>
              Refresh live status
            </button>
            <a
              className="button secondary"
              href="http://localhost:28761"
              target="_blank"
              rel="noreferrer"
            >
              Service registry
            </a>
          </div>
          <p className="small-copy">
            Notifications:{' '}
            {data.notification?.unavailable
              ? 'service unavailable'
              : paused
                ? 'paused'
                : 'consuming'}{' '}
            / Stored messages: {data.notification?.messages ?? '-'} / Lending events awaiting
            publication: {data.lending.pending}
          </p>
          <h2>Recent Kafka events</h2>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Event</th>
                  <th>Published</th>
                  <th>Attempts</th>
                  <th>Demonstrate</th>
                </tr>
              </thead>
              <tbody>
                {data.lending.events.map((event) => (
                  <tr key={event.event_id}>
                    <td>
                      <strong>{event.event_type}</strong>
                      <small className="cell-secondary">{event.event_id}</small>
                      {event.last_error && <small>{event.last_error}</small>}
                    </td>
                    <td>{event.published_at ? 'Yes' : 'Pending'}</td>
                    <td>{event.attempts}</td>
                    <td>
                      <button
                        className="button small secondary"
                        disabled={busy || !event.published_at}
                        onClick={() =>
                          action(
                            `/api/admin/events/${event.event_id}/replay`,
                            'Original event queued again. Its existing result will not be duplicated.',
                          )
                        }
                      >
                        Replay same event
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <h2>Reservation totals</h2>
          <div className="demo-toolbar">
            {data.statistics.map((row) => (
              <span className="role-tag" key={row.status}>
                {row.status}: {row.total}
              </span>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

import { useEffect, useState } from 'react';
import { api, day } from './api';
import { Badge, Empty, FormError, Icon, Loading } from './components';

export default function Dashboard({ user, refresh, go, config }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const admin = user.role === 'ADMIN';
  useEffect(() => {
    let active = true;
    setError('');
    api('/api/dashboard')
      .then((result) => {
        if (active) setData(result);
      })
      .catch((failure) => {
        if (active) setError(failure.message);
      });
    return () => {
      active = false;
    };
  }, [refresh]);
  if (error) return <FormError error={error} />;
  if (!data) return <Loading label="Preparing your overview..." />;
  const stats = [
    {
      title: 'Pending review',
      value: data.pending,
      icon: 'clock',
      key: 'PENDING',
      color: 'amber',
      caption: admin ? 'Requests awaiting a decision' : 'Waiting for the desk',
    },
    {
      title: 'Approved',
      value: data.approved,
      icon: 'calendar',
      key: 'APPROVED',
      color: 'teal',
      caption: 'Reservations ready for collection',
    },
    {
      title: 'On loan',
      value: data.checkedOut,
      icon: 'box',
      key: 'CHECKED_OUT',
      color: 'blue',
      caption: admin ? 'Equipment currently checked out' : 'Your current equipment',
    },
    {
      title: 'Overdue',
      value: data.overdue,
      icon: 'activity',
      key: 'OVERDUE',
      color: 'rose',
      caption: 'Returns needing attention',
    },
  ];
  return (
    <>
      <section className="welcome-panel">
        <div>
          <span className="eyebrow">
            {admin ? 'AT THE EQUIPMENT DESK' : 'MAKE ROOM FOR YOUR NEXT IDEA'}
          </span>
          <h2>{admin ? 'A clear view of the desk.' : `Hello, ${user.fullName.split(' ')[0]}.`}</h2>
          <p>
            {admin
              ? 'Review requests, coordinate pickups and keep equipment moving.'
              : 'Your next project starts with the right tools. Find your equipment and reserve a time.'}
          </p>
          <button className="button primary" onClick={() => go(admin ? 'requests' : 'equipment')}>
            {admin ? 'Review requests' : 'Explore equipment'}
            <Icon name="arrow" size={18} />
          </button>
        </div>
        <div className="welcome-emblem">
          <Icon name={admin ? 'calendar' : 'camera'} size={76} />
          <span>{data.readyCount} assets in service</span>
        </div>
      </section>
      <div className="stat-grid">
        {stats.map((stat) => (
          <button
            key={stat.title}
            className="stat-tile"
            onClick={() => go('requests', null, stat.key)}
          >
            <span className="stat-label">
              {stat.title}
              <span className={`stat-icon ${stat.color}`}>
                <Icon name={stat.icon} size={18} />
              </span>
            </span>
            <strong>{stat.value.toString().padStart(2, '0')}</strong>
            <span className="stat-caption">
              {stat.caption}
              <Icon name="arrow" size={15} />
            </span>
          </button>
        ))}
      </div>
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Recent requests</h2>
            <p>
              {admin
                ? 'The latest activity across the equipment desk.'
                : 'Keep track of your reservations and returns.'}
            </p>
          </div>
          <button className="text-button" onClick={() => go('requests')}>
            View all
            <Icon name="arrow" size={16} />
          </button>
        </div>
        {data.recentRequests.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Equipment</th>
                  {admin && <th>Requester</th>}
                  <th>Reservation dates</th>
                  <th>Status</th>
                  <th>
                    <span className="sr-only">Open request</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.recentRequests.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <button className="table-name" onClick={() => go('requests', r.id)}>
                        {r.equipmentName}
                      </button>
                      <span className="cell-secondary">
                        {r.reference} / {r.assetTag}
                      </span>
                    </td>
                    {admin && <td>{r.requesterName}</td>}
                    <td>
                      {day(r.startDate)}
                      <span className="cell-secondary">to {day(r.endDate)}</span>
                    </td>
                    <td>
                      <Badge status={r.status} overdue={r.overdue} />
                    </td>
                    <td>
                      <button
                        className="icon-button"
                        aria-label={`Open ${r.reference}`}
                        onClick={() => go('requests', r.id)}
                      >
                        <Icon name="chevron" size={18} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty
            title="Your first request starts here"
            action={
              <button className="button primary" onClick={() => go('equipment')}>
                Browse equipment
              </button>
            }
          >
            Choose equipment from the catalogue and send a reservation request.
          </Empty>
        )}
      </section>
      <div className="desk-note">
        <Icon name="info" size={20} />
        <div>
          <strong>A little planning goes a long way.</strong>
          <p>
            Reserve up to {config.maxDays} calendar days at a time, within the next{' '}
            {config.advanceDays} days. Bring your student card when collecting equipment. All dates
            follow campus time in Singapore.
          </p>
        </div>
      </div>
    </>
  );
}

import { useEffect, useState } from 'react';
import { api, moment } from './api';
import { Empty, FormError, Icon, Loading, Pagination } from './components';

export default function Activity({ refresh }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api(`/api/admin/activity?page=${page}`)
      .then((result) => {
        if (active) setData(result);
      })
      .catch((failure) => {
        if (active) setError(failure.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, refresh]);
  return (
    <>
      <div className="desk-note activity-intro">
        <Icon name="shield" size={21} />
        <div>
          <strong>Decisions leave a record.</strong>
          <p>
            Equipment changes, reservation decisions and loan transitions are recorded with an actor
            and timestamp.
          </p>
        </div>
      </div>
      <FormError error={error} />
      <section className="panel">
        {loading ? (
          <Loading />
        ) : !data?.items.length ? (
          <Empty title="The activity log is clear" icon="activity">
            New desk actions will appear here.
          </Empty>
        ) : (
          <ol className="activity-list">
            {data.items.map((event) => (
              <li key={event.id}>
                <span
                  className={`activity-marker ${event.targetType === 'EQUIPMENT' ? 'equipment-marker' : ''}`}
                >
                  <Icon name={event.targetType === 'EQUIPMENT' ? 'box' : 'calendar'} size={19} />
                </span>
                <div>
                  <span className="activity-label">
                    {event.action.replaceAll('_', ' ').toLowerCase()}
                  </span>
                  <p>{event.details}</p>
                  <span className="activity-meta">
                    {event.actor} <span>/</span>{' '}
                    {event.targetType === 'RESERVATION'
                      ? `REQ-${String(event.targetId).padStart(5, '0')}`
                      : `Asset #${event.targetId}`}
                  </span>
                </div>
                <time dateTime={event.createdAt}>{moment(event.createdAt)}</time>
              </li>
            ))}
          </ol>
        )}
      </section>
      <Pagination data={loading ? null : data} onPage={setPage} />
    </>
  );
}

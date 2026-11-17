import axios from 'axios';

let csrfToken;

export async function refreshCsrf() {
  const response = await axios.get('/api/auth/csrf', {
    withCredentials: true,
    headers: { 'Accept-Language': 'en' },
  });
  csrfToken = response.data;
}

export async function api(path, options = {}) {
  const { method = 'GET', body, form = false, silent = false } = options;
  const headers = { Accept: 'application/json', 'Accept-Language': 'en' };
  if (!['GET', 'HEAD'].includes(method)) {
    if (!csrfToken) await refreshCsrf();
    headers[csrfToken.headerName] = csrfToken.token;
  }
  if (body !== undefined)
    headers['Content-Type'] = form ? 'application/x-www-form-urlencoded' : 'application/json';
  let response;
  try {
    response = await axios.request({
      url: path,
      method,
      withCredentials: true,
      headers,
      validateStatus: () => true,
      data: body === undefined ? undefined : form ? new URLSearchParams(body).toString() : body,
    });
  } catch {
    throw new Error(
      'The equipment desk could not be reached. Check your connection and try again.',
    );
  }
  if (response.status === 204) return null;
  const data = response.data || {};
  if (response.status < 200 || response.status >= 300) {
    if (response.status === 401 && !silent && path !== '/api/auth/login')
      window.dispatchEvent(new Event('session-expired'));
    const error = new Error(data.message || 'The request could not be completed.');
    error.fields = data.fields || {};
    error.status = response.status;
    throw error;
  }
  return data;
}

export function query(values) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== '' && value !== undefined && value !== null && value !== false)
      params.set(key, value);
  });
  return params.toString();
}

export const categories = {
  CAMERA: 'Cameras',
  AUDIO: 'Audio',
  COMPUTING: 'Computing',
  LAB: 'Lab equipment',
  ACCESSORIES: 'Accessories',
};
export const statuses = {
  PENDING: 'Pending review',
  APPROVED: 'Approved',
  CHECKED_OUT: 'On loan',
  RETURNED: 'Returned',
  REJECTED: 'Declined',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  AVAILABLE: 'Available',
  ON_LOAN: 'On loan',
  RESERVED: 'Reserved',
  READY: 'In service',
  MAINTENANCE: 'Maintenance',
  RETIRED: 'Retired',
};
export const day = (value) =>
  value && !Number.isNaN(new Date(`${value}T00:00:00Z`).getTime())
    ? new Intl.DateTimeFormat('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        timeZone: 'UTC',
      }).format(new Date(`${value}T00:00:00Z`))
    : '-';
export const moment = (value) =>
  value
    ? new Intl.DateTimeFormat('en-GB', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
        timeZone: 'Asia/Singapore',
      }).format(new Date(value))
    : '-';
export function addDays(value, amount) {
  if (!value) return '';
  const date = new Date(`${value}T00:00:00Z`);
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) return '';
  date.setUTCDate(date.getUTCDate() + amount);
  return date.toISOString().slice(0, 10);
}

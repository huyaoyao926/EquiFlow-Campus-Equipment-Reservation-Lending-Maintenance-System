import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { installEnglishValidation } from './englishValidation';
import { HashRouter } from 'react-router-dom';
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles.css';

installEnglishValidation();

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </React.StrictMode>,
);

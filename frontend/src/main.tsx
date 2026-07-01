import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

// React 진입점. index.html의 <div id="root">를 찾아 그 안에 App을 그린다.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

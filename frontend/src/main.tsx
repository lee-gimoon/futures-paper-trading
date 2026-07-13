// 'react'는 설치된 React 라이브러리를 가리키는 모듈 이름이며,
// React 라이브러리의 기본 export(React API 객체)를 React라는 이름으로 가져온다.
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

// 'react'는 설치된 React 라이브러리를 가리키는 모듈 이름이며,
// React 라이브러리의 기본 export(React API 객체)를 React라는 이름으로 가져온다.
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

// React란?
// → 컴포넌트와 상태를 이용해 UI를 선언적으로 만들고 갱신하도록 돕는 JavaScript 라이브러리다.
// → 선언적이란 화면을 어떻게 하나씩 바꿀지가 아니라,
//   현재 상태에서 어떤 화면이 보여야 하는지를 작성하는 방식이다.
//
// React 패키지
//   → 개발자가 컴포넌트·상태·JSX로 화면을 작성할 수 있도록
//      useState 같은 JavaScript 함수와 React.StrictMode 같은 개발 중 검사 기능을 제공한다.
//      또한 React는 컴포넌트 함수를 호출하고, useState로 만든 상태를 기억하며,
//      상태가 바뀌면 컴포넌트를 다시 실행해 새 화면 구조를 계산하도록 하는
//      JavaScript 라이브러리다.
//      (JSX 문법을 JavaScript로 변환하는 일은 Vite가 담당한다.)
//
// ReactDOM 패키지
//   → React가 계산한 화면 구조를 브라우저의 실제 DOM 요소로 생성·수정해
//      화면에 반영하는 JavaScript 라이브러리다.
//
// 예: 사용자가 버튼을 클릭하면 운영체제 입력 시스템과 브라우저의 내장 DOM 이벤트 엔진이
// 클릭을 감지한다. 브라우저 이벤트 시스템은 ReactDOM의 이벤트 처리 코드에 이벤트를 전달하고,
// ReactDOM은 클릭된 버튼의 onClick 속성에 개발자가 등록한 함수를 찾아 실행되도록 연결한다.
// 그 함수에서 useState가 반환한 setter 함수(예: setPrice)가 호출되어 상태가 바뀌면 React는 해당 컴포넌트를 다시 실행해
// 새 화면 구조를 계산하고, ReactDOM은 변경된 부분을 실제 DOM에 반영한다.

// React 진입점. index.html의 <div id="root">를 찾아 그 안에 App을 그린다.
ReactDOM.createRoot(document.getElementById('root')!).render(
  // JSX 문법: JavaScript/TypeScript 안에 HTML과 비슷한 태그 표기법으로 React UI 구조를 작성하는 문법이며, Vite가 JavaScript로 변환한다.
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// React, ReactDOM, 브라우저 DOM 객체의 관계:
// - document.getElementById('root')는 index.html에 이미 만들어진 div#root DOM 객체를 찾는다.
// - ReactDOM은 React 컴포넌트가 계산한 화면 구조를 브라우저 DOM에 반영하는 라이브러리다.
// - ReactDOM.createRoot(div#root)는 div#root 내부를 관리할 React root 객체를 만든다.
// - React root 객체는 div#root에 연결된 React 앱의 컴포넌트 트리와
//   상태 변경에 따른 화면 갱신 작업을 관리하며 render(...)를 제공한다.

// - <App />은 App 컴포넌트를 렌더링하라는 React 화면 설명 객체다. 이 시점에는 App()을 실행하지 않는다.
// - render(...)가 호출되면 React가 App()을 실행해 화면 구조를 계산하고,
//   ReactDOM이 그 결과를 div#root 내부의 실제 DOM 요소로 생성·수정한다.

// 브라우저에서 React 앱이 실행되고 화면이 표시되는 흐름:
// 사용자가 http://localhost:5173/에 접속
//   ↓
// Vite 개발 서버가 index.html 응답
//   ↓
// 브라우저가 index.html을 읽어 div#root DOM 요소를 생성
//   ↓
// 브라우저가 <script type="module" src="/src/main.tsx">를 발견하고 main.tsx 요청
//   ↓
// Vite가 main.tsx의 TypeScript·JSX 문법을 브라우저용 JavaScript로 변환해 응답
//   ↓
// 브라우저가 import를 보고 React·ReactDOM·App 모듈도 요청
//   ↓
// Vite가 node_modules의 React 관련 JavaScript 라이브러리를 찾아,
// 브라우저가 HTTP 요청으로 import할 수 있는 ES 모듈 URL로 준비해 전달
//   ↓
// 브라우저가 필요한 모듈을 받은 뒤 이 파일의 createRoot(...).render(<App />) 실행
//   ↓
// 브라우저 안에서 실행 중인 React 라이브러리 코드가 App() 함수를 호출해
// 현재 상태에 필요한 화면 구조를 계산
//   ↓
// 브라우저 안에서 실행 중인 ReactDOM 라이브러리 코드가 그 결과를
// div#root 내부의 실제 DOM 요소로 생성·수정
//   ↓
// 브라우저 렌더링 엔진이 변경된 DOM을 화면 픽셀로 표시

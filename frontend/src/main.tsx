// 'react'는 설치된 React 패키지를 가리키는 모듈 지정자다.
// Vite가 이 지정자를 실제 패키지 모듈로 해석하며, 가져온 React 객체는 아래의 React.StrictMode에 사용한다.
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
//      JSX를 브라우저가 실행할 JavaScript로 변환하는 일은 Vite의 변환 과정이 담당한다.
//
// ReactDOM 패키지
//   → React와 브라우저 DOM을 연결하는 렌더러 라이브러리다.
//      React가 계산한 화면 구조에 따라 실제 DOM 요소를 생성·수정한다.
//      변경된 DOM을 실제 화면에 표시하는 일은 이후 브라우저 렌더링 과정에서 수행된다.
//
// 예: 사용자가 버튼을 클릭하면 운영체제 입력 시스템과 브라우저의 내장 DOM 이벤트 엔진이
// 클릭을 감지한다. 브라우저가 DOM 이벤트를 전달하면 ReactDOM의 이벤트 처리 코드가
// 해당 요소의 onClick에 연결된 개발자 함수를 호출한다. 그 함수에서 useState가 반환한
// setter 함수(예: setPrice)가 호출되면 React가 새 화면 구조를 계산하고,
// ReactDOM이 변경된 부분을 실제 DOM에 반영한다.

// React 진입점. index.html의 <div id="root">를 찾아 그 안에 App을 그린다.
ReactDOM.createRoot(document.getElementById('root')!).render(
  // JSX는 JavaScript/TypeScript 안에 HTML과 비슷한 표기법으로 React UI 구조를 작성하는 문법이다.
  // Vite가 JSX를 브라우저에서 실행할 JavaScript 표현으로 변환한다.
  <React.StrictMode>
    {/* <App />은 App 컴포넌트를 렌더링하라는 React 요소 설명이며, 여기서 App()을 직접 호출하지 않는다. */}
    {/* React는 예약된 렌더링 작업에서 App 함수를 호출하고 반환된 화면 구조를 처리한다. */}
    <App />
  </React.StrictMode>
);

// React, ReactDOM, 브라우저 DOM 객체의 관계:
// - document.getElementById('root')는 index.html에 이미 만들어진 div#root DOM 객체를 찾는다.
// - ReactDOM.createRoot(div#root)는 div#root 내부를 관리할 React root 객체를 만든다.
// - React root 객체는 div#root에 연결된 React 앱의 컴포넌트 트리와
//   상태 변경에 따른 화면 갱신 작업을 관리하며 render(...)를 제공한다.
// - <App />은 App 컴포넌트를 렌더링하라는 React 요소 설명이다.
// - render(<App />)는 초기 렌더링을 요청한다. React가 렌더링을 시작하면 App()을 호출해
//   화면 구조를 계산하고, ReactDOM이 그 결과를 div#root 내부의 실제 DOM에 반영한다.

// 브라우저에서 React 앱이 실행되고 화면이 표시되는 흐름:
//
// 사용자가 http://localhost:5173/에 접속
//   ↓
// Vite 개발 서버가 index.html 응답
//   ↓
// 이벤트 루프 처리 과정에서 HTML 파싱 작업이 선택되면
// 렌더러 메인 스레드가 Blink의 HTML 파서를 실행해 div#root DOM 요소를 생성
//   ↓
// HTML 파서가 <script type="module" src="/src/main.tsx">를 발견하고
// 브라우저가 main.tsx 요청
//   ↓
// Vite가 main.tsx의 TypeScript·JSX를 JavaScript로 변환해 응답
//   ↓
// 브라우저가 변환된 main.tsx의 정적 import를 확인하고
// React·ReactDOM·App·styles.css 등의 의존 파일을 각각 요청
//   ↓
// Vite가 각 요청을 처리해 소스 파일은 변환하고,
// node_modules의 패키지는 브라우저가 불러올 수 있는 모듈로 제공
//   ↓
// main.tsx 진입 모듈과 정적으로 import된 의존 모듈이 로드·연결되고
// HTML 파싱이 끝나면 브라우저가 모듈 스크립트를 실행할 수 있는 상태가 됨
//   ↓
// 이벤트 루프 처리 과정에서 모듈 스크립트를 실행할 차례가 되면
// 렌더러 메인 스레드의 V8이 JavaScript 의존 모듈을 필요한 순서로 평가한 뒤
// main.tsx 파일을 변환해 만든 JavaScript 진입 모듈의 최상위 코드를 평가
//   ↓
// main.tsx의 최상위 코드에서 createRoot(div#root)가 React root를 만들고
// render(<App />)가 초기 렌더링을 요청
//   ↓
// React가 예약된 렌더링 작업에서 App 컴포넌트를 호출해 화면 구조를 계산
// 개발 환경의 StrictMode에서는 오류 검사를 위해 컴포넌트 렌더링이 한 번 더 수행될 수 있음
//   ↓
// ReactDOM이 계산 결과를 div#root 내부의 실제 DOM에 반영
//   ↓
// DOM 반영 작업이 끝나고 렌더링 기회가 오면 렌더러 메인 스레드가
// Blink의 스타일·레이아웃·페인트 준비 코드를 실행하고, 이후 합성·래스터 과정을 거쳐 화면에 표시

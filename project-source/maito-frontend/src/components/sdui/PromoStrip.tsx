import React, { useState } from 'react';

export interface PromoStripProps {
  sectionId?: string;
  payload: {
    text: string;
    backgroundColor?: string;
    textColor?: string;
    link?: string;
  };
}

export const PromoStrip: React.FC<PromoStripProps> = ({ payload }) => {
  const [visible, setVisible] = useState(true);
  if (!visible) return null;

  return (
    <div
      className="relative py-2.5 px-4 text-center text-xs sm:text-sm font-semibold tracking-wide flex items-center justify-center transition-all duration-300"
      style={{
        backgroundColor: payload.backgroundColor || '#0F172A',
        color: payload.textColor || '#F8FAFC'
      }}
    >
      <div className="flex items-center space-x-2">
        <span className="inline-block px-1.5 py-0.5 rounded text-[10px] font-extrabold uppercase bg-amber-500 text-slate-900 tracking-wider">
          OFFER
        </span>
        <span>{payload.text}</span>
      </div>
      <button
        onClick={() => setVisible(false)}
        className="absolute right-4 p-1 hover:opacity-75 focus:outline-none"
        aria-label="Dismiss banner"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
};

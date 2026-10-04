import React from 'react';

export interface HeroCarouselProps {
  payload: {
    slides: Array<{
      title: string;
      subtitle: string;
      ctaText: string;
      ctaLink: string;
      imageUrl: string;
    }>;
  };
}

export const HeroCarousel: React.FC<HeroCarouselProps> = ({ payload }) => {
  const slides = payload.slides || [];
  if (!slides.length) return null;
  const slide = slides[0];

  return (
    <section className="relative bg-slate-950 overflow-hidden text-white">
      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 lg:py-32 flex flex-col lg:flex-row items-center justify-between gap-12 z-10">
        <div className="max-w-xl text-center lg:text-left space-y-6">
          <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-amber-500/20 text-amber-400 border border-amber-500/30 text-xs font-semibold uppercase tracking-wider">
            <span className="w-2 h-2 rounded-full bg-amber-400"></span>
            <span>100% Traceable Mithila Foxnuts</span>
          </div>
          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight leading-tight text-white font-heading">
            {slide.title}
          </h1>
          <p className="text-lg text-slate-300 font-normal leading-relaxed">
            {slide.subtitle}
          </p>
          <div className="pt-4 flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-4">
            <a
              href={slide.ctaLink || '#featured'}
              className="w-full sm:w-auto px-8 py-4 text-slate-950 font-bold rounded-xl shadow-lg hover:shadow-amber-500/25 transition-all text-center flex items-center justify-center space-x-2"
              style={{ backgroundColor: 'var(--color-primary, #D97706)' }}
            >
              <span>{slide.ctaText || 'Shop Collection'}</span>
              <svg className="w-5 h-5 ml-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
              </svg>
            </a>
            <a
              href="#story"
              className="w-full sm:w-auto px-6 py-4 text-white font-semibold rounded-xl border border-slate-700 hover:bg-slate-800/60 transition-colors text-center"
            >
              Explore Origins
            </a>
          </div>
        </div>

        <div className="w-full lg:max-w-lg relative group">
          <div className="absolute -inset-1 rounded-3xl bg-gradient-to-r from-amber-500 to-amber-700 opacity-30 blur-2xl group-hover:opacity-50 transition-opacity"></div>
          <div className="relative overflow-hidden rounded-2xl shadow-2xl border border-slate-800 bg-slate-900">
            <img
              src={slide.imageUrl}
              alt={slide.title}
              className="w-full h-[380px] sm:h-[440px] object-cover hover:scale-105 transition-transform duration-700"
            />
          </div>
        </div>
      </div>
    </section>
  );
};

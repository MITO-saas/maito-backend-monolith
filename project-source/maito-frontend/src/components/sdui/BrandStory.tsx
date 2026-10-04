import React from 'react';

export interface BrandStoryProps {
  payload: {
    heading?: string;
    body?: string;
    badge?: string;
  };
}

export const BrandStory: React.FC<BrandStoryProps> = ({ payload }) => {
  return (
    <section id="story" className="bg-amber-50/60 border-y border-amber-100/80 py-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
          <div className="space-y-6">
            <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-amber-500/10 text-amber-700 text-xs font-bold uppercase tracking-wider">
              <span>Mithila Sourcing Heritage</span>
            </div>
            <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-900 leading-tight font-heading">
              {payload.heading || 'From Pond to Pack'}
            </h2>
            <p className="text-slate-700 text-base sm:text-lg leading-relaxed font-normal">
              {payload.body || 'Directly sourced from Bihar farmers, roasted with zero trans-fats.'}
            </p>
            <div className="grid grid-cols-2 gap-4 pt-4">
              <div className="bg-white p-4 rounded-xl border border-amber-100 shadow-sm">
                <span className="block text-2xl font-black text-amber-600">100%</span>
                <span className="text-xs font-semibold text-slate-600">Organic Wetland Harvest</span>
              </div>
              <div className="bg-white p-4 rounded-xl border border-amber-100 shadow-sm">
                <span className="block text-2xl font-black text-amber-600">Zero</span>
                <span className="text-xs font-semibold text-slate-600">Trans-Fat & Preservatives</span>
              </div>
            </div>
          </div>
          <div className="relative">
            <div className="rounded-3xl overflow-hidden shadow-2xl border-4 border-white">
              <img
                src="/assets/brands/mito_crunch/banners/promo_holi_sale.webp"
                alt="Heritage Sourcing"
                className="w-full h-80 sm:h-96 object-cover"
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

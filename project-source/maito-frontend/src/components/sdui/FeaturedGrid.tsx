import React from 'react';

export interface FeaturedGridProps {
  payload: {
    heading?: string;
    items: Array<{
      name: string;
      tag?: string;
      imageUrl?: string;
      price?: number;
    }>;
  };
  onAddToCart?: (name: string) => void;
}

export const FeaturedGrid: React.FC<FeaturedGridProps> = ({ payload, onAddToCart }) => {
  const items = payload.items || [];

  return (
    <section id="featured" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
      <div className="text-center max-w-2xl mx-auto mb-14">
        <span className="text-xs font-bold uppercase tracking-widest text-amber-600">Pure Roasted Goodness</span>
        <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-900 mt-2 font-heading">
          {payload.heading || 'Trending Flavors'}
        </h2>
        <p className="text-slate-600 mt-3 text-sm sm:text-base">
          Slowly roasted to perfection with cold-pressed olive oil and natural Himalayan herbs.
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-8">
        {items.map((item, idx) => (
          <div
            key={idx}
            className="bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300 border border-slate-100 flex flex-col group"
          >
            <div className="relative bg-slate-50 overflow-hidden aspect-square flex items-center justify-center">
              <img
                src={item.imageUrl || '/assets/brands/mito_crunch/products/makhana_peri_peri.webp'}
                alt={item.name}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              />
              {item.tag && (
                <span className="absolute top-4 left-4 bg-white/95 backdrop-blur-md px-3 py-1 rounded-full text-xs font-bold text-slate-800 shadow-sm border border-slate-100">
                  {item.tag}
                </span>
              )}
            </div>
            <div className="p-6 flex flex-col flex-grow justify-between space-y-4">
              <div>
                <h3 className="text-xl font-bold text-slate-900 group-hover:text-amber-600 transition-colors">
                  {item.name}
                </h3>
                <p className="text-xs text-slate-500 mt-1">70g Pouch | 100% Organic Foxnuts | 0% Trans Fat</p>
              </div>
              <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                <div className="flex items-baseline space-x-1">
                  <span className="text-2xl font-black text-slate-900">₹{item.price || 199}</span>
                  <span className="text-xs text-slate-400 line-through">₹249</span>
                </div>
                <button
                  onClick={() => onAddToCart && onAddToCart(item.name)}
                  className="px-4 py-2.5 bg-slate-900 hover:bg-amber-600 text-white text-xs font-bold rounded-xl transition-all flex items-center space-x-1.5 shadow-sm active:scale-95"
                >
                  <span>Add to Cart</span>
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
};

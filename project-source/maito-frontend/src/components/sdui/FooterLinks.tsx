import React from 'react';

export interface FooterLinksProps {
  payload: {
    social?: { instagram?: string; twitter?: string };
    policies?: string[];
  };
}

export const FooterLinks: React.FC<FooterLinksProps> = ({ payload }) => {
  const policies = payload.policies || ['Privacy Policy', 'Terms of Service', 'Refund Policy'];
  const social = payload.social || { instagram: '@mitocrunch' };

  return (
    <footer className="bg-slate-950 text-slate-400 py-16 border-t border-slate-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-10 pb-12 border-b border-slate-800">
          <div className="md:col-span-2 space-y-4">
            <img src="/assets/brands/mito_crunch/brand/logo_crunch.svg" alt="Mito Crunch" className="h-9 w-auto brightness-200" />
            <p className="text-sm text-slate-400 max-w-sm">
              Premium organic roasted makhana handpicked from the wetlands of Mithila, Bihar. Nutritious snacking crafted for modern lifestyles.
            </p>
            <div className="text-xs text-amber-400 font-semibold">
              Follow us: <a href="https://instagram.com" className="hover:underline">{social.instagram || '@mitocrunch'}</a>
            </div>
          </div>
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-4">Policies & Compliance</h4>
            <ul className="space-y-2 text-sm">
              {policies.map((p, idx) => (
                <li key={idx}><a href="#" className="hover:text-amber-400 transition-colors">{p}</a></li>
              ))}
            </ul>
          </div>
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-4">Platform Architecture</h4>
            <p className="text-xs text-slate-500 leading-relaxed">
              Powered by Maito Server-Driven UI (SDUI) Engine with isolated PostgreSQL multi-tenancy & immutable static asset storage.
            </p>
          </div>
        </div>
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-500 gap-4">
          <span>&copy; {new Date().getFullYear()} Mito Crunch. All rights reserved.</span>
          <span>Engineered with 30-Year High-Availability Standards</span>
        </div>
      </div>
    </footer>
  );
};

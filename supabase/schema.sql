-- Online track directory for A2B (Supabase project "a2b").
-- The tags stay the source of truth for a track and its leaderboard; this table
-- only lets the app show every published track on the map.
-- Anyone may read and add tracks; nobody can change or delete them via the API.

create table public.tracks (
    stamp             integer primary key check (stamp > 0),
    name              text not null check (char_length(name) between 1 and 100),
    description       text not null default '' check (char_length(description) <= 1000),
    horizontal_meters integer not null,
    vertical_meters   integer not null,
    start_lat         double precision not null check (start_lat between -90 and 90),
    start_lng         double precision not null check (start_lng between -180 and 180),
    finish_lat        double precision not null check (finish_lat between -90 and 90),
    finish_lng        double precision not null check (finish_lng between -180 and 180),
    created_at        timestamptz not null default now()
);

alter table public.tracks enable row level security;

grant select, insert on public.tracks to anon, authenticated;

create policy "Tracks are public"
    on public.tracks for select
    to anon, authenticated
    using (true);

create policy "Anyone can publish a track"
    on public.tracks for insert
    to anon, authenticated
    with check (true);

// reLation — the graph of a relation: its objects as vertices, an edge wherever the relation holds, and the
// walks one can ask of it.
//
// A graph forgets the names of the steps and keeps who is next to whom. Each edge has a cost: 1 (the default:
// distance counts steps), the relation's own values (\value: a relation of lawvere costs), or what a rule
// (x, y) -> number answers (RLGraph.voiceLeading: the steps the voices move).
//
//   ~cd = RLRelationContext.cubeDance;
//   ~g = ~cd.graph;
//   ~c = ~cd.space.major(0);
//   ~g.neighbours(~c);                            // [C minor, E minor, C aug]
//   ~g.distance(~c, ~cd.space.major(6));          // 6
//   ~g.shortestPath(~c, ~cd.space.major(1));      // 3 steps, through F minor or two augmented triads
//   ~g.countHamiltonianCycles;                    // 1296: the walks through all 28 chords, back to the start
//   ~g.hamiltonianCycle;                          // the first of them
//
// The search for Hamiltonian cycles is exact and meant for graphs of some tens of vertices.

RLGraph {
	var <objects, <relation, adjacency, costs, index, unit;

	*new { |relation, cost| ^super.new.prInit(relation, cost) }

	// The graph of a relation of chords of one RLEDO, each edge costing the steps its voices move.
	*voiceLeading { |relation| ^this.new(relation, { |x, y| RLVoiceLeading.stepsBetween(x, y) }) }

	prInit { |rel, cost|
		var zero = rel.quantale.zero;
		if(rel.follows(rel).not) { Error("RLGraph: a relation of one list to itself is needed").throw };
		relation = rel;
		objects = rel.source;
		index = Dictionary.new(objects.size * 2);
		objects.do { |x, i| index[x] = i };
		adjacency = Array.new;
		costs = Array.new;
		unit = true;
		rel.rows.do { |row, i|
			var adj = Array.new, cs = Array.new;
			row.do { |v, j|
				var c;
				if((v != zero) and: { i != j }) {
					c = case
					{ cost.isNil } { 1 }
					{ cost == \value } { v }
					{ cost.value(objects[i], objects[j]) };
					if(c != 1) { unit = false };
					adj = adj.add(j);
					cs = cs.add(c);
				};
			};
			adjacency = adjacency.add(adj);
			costs = costs.add(cs);
		};
	}

	prIndex { |x| ^index[x] ?? { Error("RLGraph: % is not one of the vertices".format(x)).throw } }

	size { ^objects.size }
	edgeCount { ^adjacency.sum(_.size).div(if(this.isSymmetric) { 2 } { 1 }) }
	isSymmetric { ^adjacency.every { |adj, i| adj.every { |j| adjacency[j].includes(i) } } }

	// The place of an object among the vertices (nil when it is not one); and every edge as
	// [i, j, cost, both ways], i and j places among the vertices. An edge that goes both ways is listed once,
	// the smaller place first.
	indexOf { |x| ^index[x] }
	edges {
		var out = Array.new;
		adjacency.do { |adj, i|
			adj.do { |j, k|
				var back = adjacency[j].includes(i);
				if(back.not or: { i < j }) { out = out.add([i, j, costs[i][k], back]) };
			};
		};
		^out
	}

	// Every cycle of at most `max` vertices that passes no vertex twice, each once: it starts on its first
	// vertex in the order of the vertices and, where every edge goes both ways, is listed in one direction.
	cycles { |max = 6|
		var out = Array.new, sym = this.isSymmetric, least = if(sym) { 3 } { 2 }, go;
		go = { |start, path|
			adjacency[path.last].do { |j|
				if(j == start) {
					if((path.size >= least) and: { sym.not or: { path[1] < path.last } }) { out = out.add(path.collect { |i| objects[i] }) };
				} {
					if((j > start) and: { path.includes(j).not } and: { path.size < max }) { go.value(start, path ++ [j]) };
				};
			};
		};
		objects.size.do { |s| go.value(s, [s]) };
		^out
	}

	neighbours { |x| ^adjacency[this.prIndex(x)].collect { |j| objects[j] } }
	degree { |x| ^adjacency[this.prIndex(x)].size }
	degrees { ^adjacency.collect(_.size) }
	cost { |x, y|
		var i = this.prIndex(x), k = adjacency[i].indexOf(this.prIndex(y));
		^k !? { costs[i][k] }
	}

	// The least cost from an object to every object, in the order of objects (inf where no walk leads), and
	// for each the vertices a cheapest walk can come from.
	prSearch { |from|
		var n = objects.size, dist = Array.fill(n, inf), prev = Array.fill(n) { Array.new }, done = Array.fill(n, false);
		var s = this.prIndex(from), current;
		dist[s] = 0;
		n.do {
			current = nil;
			n.do { |i| if(done[i].not and: { dist[i] < inf } and: { current.isNil or: { dist[i] < dist[current] } }) { current = i } };
			if(current.notNil) {
				done[current] = true;
				adjacency[current].do { |j, k|
					var d = dist[current] + costs[current][k];
					if(d < (dist[j] - 1e-9)) { dist[j] = d; prev[j] = [current] } {
						if((d - dist[j]).abs <= 1e-9 and: { prev[j].includes(current).not }) { prev[j] = prev[j].add(current) };
					};
				};
			};
		};
		^[dist, prev]
	}

	distances { |from| ^this.prSearch(from)[0] }
	distance { |x, y| ^this.prSearch(x)[0][this.prIndex(y)] }

	// One cheapest walk from x to y, as the list of objects it meets (both ends in): the first in the order
	// of the objects. nil when no walk leads there.
	shortestPath { |x, y|
		var prev = this.prSearch(x)[1], i = this.prIndex(y), s = this.prIndex(x), out = [i];
		while { i != s } {
			if(prev[i].isEmpty) { ^nil };
			i = prev[i].minItem;
			out = out.add(i);
		};
		^out.reverse.collect { |k| objects[k] }
	}

	// Every cheapest walk from x to y (up to max of them).
	shortestPaths { |x, y, max = 1000|
		var prev = this.prSearch(x)[1], s = this.prIndex(x), out = Array.new, rec;
		rec = { |i, tail|
			if(out.size < max) {
				if(i == s) { out = out.add(([i] ++ tail).collect { |k| objects[k] }) } {
					prev[i].copy.sort.do { |p| rec.value(p, [i] ++ tail) };
				};
			};
		};
		rec.value(this.prIndex(y), []);
		^out
	}

	// The largest distance between two objects; the parts no edge joins.
	diameter { ^objects.collect { |x| this.distances(x).select { |d| d < inf }.maxItem }.maxItem }
	components {
		var seen = Array.fill(objects.size, false), out = Array.new;
		objects.size.do { |i|
			var queue, head = 0;
			if(seen[i].not) {
				queue = [i];
				seen[i] = true;
				while { head < queue.size } {
					adjacency[queue[head]].do { |w| if(seen[w].not) { seen[w] = true; queue = queue.add(w) } };
					head = head + 1;
				};
				out = out.add(queue.collect { |v| objects[v] });
			};
		};
		^out
	}

	// The walks through every object once that come back to their start. Counted from one start, each
	// direction once: an undirected cycle counts once (a symmetric graph), a directed one once.
	countHamiltonianCycles { |start|
		var found = this.prHamilton(start, inf, false);
		^if(this.isSymmetric and: { objects.size > 2 }) { found.div(2) } { found }
	}

	// The first cycles found (up to max), each a list of the objects in the order met, the start first.
	hamiltonianCycles { |start, max = 1| ^this.prHamilton(start, max, true) }
	hamiltonianCycle { |start| ^this.hamiltonianCycles(start, 1).first }

	// A depth-first search. In a symmetric graph, after each step, a neighbour of the head that has fewer than
	// two ways left (free neighbours, or the start for the way out) can only be the next vertex: two such
	// neighbours end the branch, one is the only move. A one-way graph is searched without that shortcut.
	prHamilton { |start, max, collect|
		var n = objects.size, s = if(start.isNil) { 0 } { this.prIndex(start) }, prune = this.isSymmetric;
		var visited = Array.fill(n, false), avail = adjacency.collect(_.size), toStart = Array.fill(n, 0);
		var path = Array.newClear(n), count = 0, found = Array.new, visit;
		adjacency.do { |adj, i| if(adj.includes(s)) { toStart[i] = 1 } };
		visit = { |v, depth|
			var forced, dead = false, adj = adjacency[v];
			visited[v] = true;
			path[depth] = v;
			adj.do { |u| avail[u] = avail[u] - 1 };
			if(depth == (n - 1)) {
				if(toStart[v] == 1) {
					count = count + 1;
					if(collect) { found = found.add(path.collect { |k| objects[k] }) };
				};
			} {
				if(prune) {
					adj.do { |u|
						if(visited[u].not and: { (avail[u] + toStart[u]) < 2 }) {
							if(forced.isNil) { forced = u } { dead = true };
						};
					};
				};
				if(dead.not) {
					if(forced.notNil) { visit.value(forced, depth + 1) } {
						adj.do { |w| if(visited[w].not and: { count < max }) { visit.value(w, depth + 1) } };
					};
				};
			};
			adj.do { |u| avail[u] = avail[u] + 1 };
			visited[v] = false;
		};
		if(n > 0) { visit.value(s, 0) };
		^if(collect) { found } { count }
	}

	printOn { |stream| stream << "RLGraph(" << objects.size << " vertices, " << this.edgeCount << " edges)" }
}
